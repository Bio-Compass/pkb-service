package com.biocompass.pkb.ci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class RunClusterE2eTest {
    private static final Path TEMPLATE = Path.of("deploy/kubernetes/e2e/job.yaml");
    private final Map<String, String> environment = new HashMap<>(Map.of(
            "PKB_E2E_NAMESPACE", "testing", "GITHUB_RUN_ID", "123", "GITHUB_RUN_ATTEMPT", "1",
            "GITHUB_SHA", "a".repeat(40),
            "PKB_E2E_RUNNER_IMAGE", "ghcr.io/bio-compass/pkb-service-e2e@sha256:" + "b".repeat(64),
            "PKB_E2E_USERNAME", "fixture@example.test", "PKB_E2E_PASSWORD", "private-password",
            "PKB_E2E_SERVICE_NAME", "testing-e2e-client", "PKB_E2E_SERVICE_TOKEN", "private-service-token"));
    @TempDir
    Path reports;

    @Test
    void createsPrivateBoundedJobAndCollectsResultsBeforeForegroundCleanup() throws Exception {
        var kube = new FakeKubernetes();
        execute(kube, Duration.ofSeconds(5));
        assertThat(Files.readString(reports.resolve("TEST-junit-jupiter.xml"))).contains("tests=\"1\"");
        assertThat(kube.inputs).anySatisfy(input -> assertThat(input)
                .contains("\"kind\":\"Secret\"", "\"ownerReferences\"", "00000000-0000-0000-0000-000000000000")
                .contains("\"service-name\":\"" + Base64.getEncoder().encodeToString("testing-e2e-client".getBytes(StandardCharsets.UTF_8)) + "\"",
                        "\"service-token\":\"" + Base64.getEncoder().encodeToString("private-service-token".getBytes(StandardCharsets.UTF_8)) + "\"")
                .doesNotContain("private-password", "private-service-token"));
        assertThat(kube.calls).allSatisfy(call -> assertThat(call).contains("--namespace=testing")
                .doesNotContain("exec", "port-forward", "private-password", "private-service-token"));
        assertThat(kube.calls.getLast()).contains("delete", "job", "--cascade=foreground");
        assertThat(kube.calls.stream().filter(call -> call.contains("wait"))).singleElement()
                .satisfies(call -> assertThat(call).contains("--request-timeout=0",
                        "--for=jsonpath={.status.conditions}", "job/pkb-e2e-123-1"));
    }

    @Test
    void missingWorkloadCredentialsFailBeforeCreatingResources() {
        for (var key : List.of("PKB_E2E_SERVICE_NAME", "PKB_E2E_SERVICE_TOKEN")) {
            var value = environment.remove(key);
            var kube = new FakeKubernetes();
            assertThatThrownBy(() -> execute(kube, Duration.ofSeconds(5)))
                    .hasMessageContaining(key).hasMessageNotContaining("private-service-token");
            assertThat(kube.calls).isEmpty();
            environment.put(key, value);
        }
    }

    @Test
    void waitsForPodsToTerminateAfterSuccessCriteriaAreMetWithinTheSameDeadline() throws Exception {
        var kube = new FakeKubernetes();
        kube.state = "SuccessCriteriaMet";
        execute(kube, Duration.ofMinutes(6));
        assertThat(kube.calls.stream().filter(call -> call.contains("wait"))).hasSize(2)
                .last().satisfies(call -> assertThat(call).contains("--for=condition=Complete"));
        assertThat(kube.waitTimeouts).hasSize(2).allSatisfy(timeout -> assertThat(timeout)
                .isBetween(Duration.ofMinutes(6), Duration.ofMinutes(6).plusSeconds(15)));
        assertThat(kube.waitTimeouts.getLast()).isLessThanOrEqualTo(kube.waitTimeouts.getFirst());
    }

    @Test
    void watchErrorsFailClosedAndStillCollectLogsAndCleanUp() {
        var kube = new FakeKubernetes();
        kube.failWait = true;
        assertThatThrownBy(() -> execute(kube, Duration.ofMinutes(6)))
                .hasMessageContaining("Wait for E2E Job failed");
        assertThat(kube.calls).anySatisfy(call -> assertThat(call).contains("logs"));
        assertThat(kube.calls.getLast()).contains("delete");
    }

    @Test
    void unexpectedJobConditionsCannotPassTheGate() {
        var kube = new FakeKubernetes();
        kube.state = "Suspended";
        assertThatThrownBy(() -> execute(kube, Duration.ofSeconds(5)))
                .hasMessageContaining("unexpected condition");
        assertThat(kube.calls.getLast()).contains("delete");
    }

    @Test
    void failsAndCleansUpWhenTheJobFailsOrTimesOut() throws Exception {
        for (var state : List.of("Failed", "FailureTarget")) {
            var failedKube = new FakeKubernetes();
            failedKube.state = state;
            assertThatThrownBy(() -> execute(failedKube, Duration.ofSeconds(5))).hasMessageContaining("Job failed");
            assertThat(failedKube.waitTimeouts).hasSize(1);
            assertThat(failedKube.calls.getLast()).contains("delete");
        }
        var timeoutKube = new FakeKubernetes();
        assertThatThrownBy(() -> execute(timeoutKube, Duration.ZERO)).hasMessageContaining("timed out");
        assertThat(timeoutKube.calls.getLast()).contains("delete");
    }

    @Test
    void rejectsMissingResultsEvenWhenKubernetesReportsSuccess() {
        var kube = new FakeKubernetes();
        kube.logs = "Process finished without JUnit execution";
        assertThatThrownBy(() -> execute(kube, Duration.ofSeconds(5))).hasMessageContaining("empty JUnit");
        assertThat(kube.calls.getLast()).contains("delete");
    }

    @Test
    void secretApiErrorsStayPrivateAndStillCleanUp() {
        var kube = new FakeKubernetes();
        kube.failSecret = true;
        assertThatThrownBy(() -> execute(kube, Duration.ofSeconds(5)))
                .hasMessageContaining("temporary E2E credentials").hasMessageNotContaining("private-password");
        assertThat(kube.calls.getLast()).contains("delete");
    }

    @Test
    void redactsWorkloadAndUserCredentialsFromLogsAndDecodedXml() throws Exception {
        var token = "private&service<token";
        environment.put("PKB_E2E_SERVICE_TOKEN", token);
        var xml = "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\">"
                + "<testcase name=\"testing-e2e-client\"><system-out>private-password "
                + "private&amp;service&lt;token</system-out></testcase></testsuite>";
        var encodedToken = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        var kube = new FakeKubernetes();
        kube.logs = "fixture@example.test private-password testing-e2e-client " + token + " " + encodedToken
                + "\n" + logs("TEST-redacted.xml", xml);
        execute(kube, Duration.ofSeconds(5));
        var safeLog = Files.readString(reports.resolve("job.log"));
        var safeXml = Files.readString(reports.resolve("TEST-redacted.xml"));
        assertThat(safeLog).doesNotContain("PKB_E2E_REPORT_BEGIN", token, encodedToken,
                "testing-e2e-client", "private-password", "fixture@example.test").contains("[REDACTED]");
        assertThat(safeXml).doesNotContain(token, "private&amp;service&lt;token", "testing-e2e-client", "private-password")
                .contains("[REDACTED]", "tests=\"1\"");
    }

    @Test
    void redactsMultilinePasswordsAcrossOriginalLogLineEndings() throws Exception {
        var password = "private\r\nmultiline-password";
        environment.put("PKB_E2E_PASSWORD", password);
        var kube = new FakeKubernetes();
        kube.logs = "diagnostic: " + password + "\r\n" + kube.logs;
        execute(kube, Duration.ofSeconds(5));
        assertThat(Files.readString(reports.resolve("job.log")))
                .doesNotContain(password, "multiline-password").contains("diagnostic: [REDACTED]");
    }

    @Test
    void redactsServiceCredentialsSplitBetweenAdjacentCdataNodes() throws Exception {
        var kube = new FakeKubernetes();
        kube.logs = logs("TEST-split.xml", "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\">"
                + "<testcase name=\"workload\"><system-out><![CDATA[private-]]><![CDATA[service-token]]>"
                + "</system-out></testcase></testsuite>");
        execute(kube, Duration.ofSeconds(5));
        assertThat(Files.readString(reports.resolve("TEST-split.xml")))
                .doesNotContain("private-", "service-token").contains("[REDACTED]");
    }

    @Test
    void failedCleanupCannotReportAPassingGate() {
        var kube = new FakeKubernetes();
        kube.failCleanup = true;
        assertThatThrownBy(() -> execute(kube, Duration.ofSeconds(5))).hasMessageContaining("Clean up E2E Job");
    }

    @Test
    void rejectsProductionAndMutableRunnerImagesBeforeAnyKubernetesOperation() {
        var kube = new FakeKubernetes();
        environment.put("PKB_E2E_NAMESPACE", "prod");
        assertThatThrownBy(() -> execute(kube, Duration.ofSeconds(5))).hasMessageContaining("namespace testing");
        environment.put("PKB_E2E_NAMESPACE", "testing");
        environment.put("PKB_E2E_RUNNER_IMAGE", "ghcr.io/bio-compass/pkb-service-e2e:latest");
        assertThatThrownBy(() -> execute(kube, Duration.ofSeconds(5))).hasMessageContaining("immutable");
        assertThat(kube.calls).isEmpty();
    }

    @Test
    void rejectsFailedSkippedAndUnsafeReports() throws Exception {
        for (var attr : List.of("failures", "errors", "skipped")) {
            var xml = "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>"
                    .replace(attr + "=\"0\"", attr + "=\"1\"");
            assertThatThrownBy(() -> RunClusterE2e.collectReports(logs("TEST-junit-jupiter.xml", xml), reports, true))
                    .hasMessageContaining("failed, errored or skipped");
        }
        assertThatThrownBy(() -> RunClusterE2e.collectReports(logs("../secret", ""), reports, true))
                .hasMessageContaining("filename");
        assertThatThrownBy(() -> RunClusterE2e.collectReports("PKB_E2E_REPORT_BEGIN:TEST-test.xml\n", reports, true))
                .hasMessageContaining("Incomplete");
    }

    @Test
    void jobHasNoApiTokenPublicAccessOrPlaintextCredentials() throws Exception {
        Map<String, Object> job = new Yaml(new SafeConstructor(new LoaderOptions()))
                .load(RunClusterE2e.render(Files.readString(TEMPLATE), environment));
        assertThat(map(job, "metadata")).containsEntry("namespace", "testing");
        var spec = map(job, "spec");
        assertThat(spec).containsEntry("backoffLimit", 0).containsEntry("activeDeadlineSeconds", 300)
                .containsEntry("ttlSecondsAfterFinished", 1800);
        var pod = map(map(spec, "template"), "spec");
        assertThat(pod).containsEntry("automountServiceAccountToken", false).containsEntry("restartPolicy", "Never")
                .doesNotContainKeys("hostNetwork", "hostPID");
        var manifest = RunClusterE2e.render(Files.readString(TEMPLATE), environment);
        assertThat(manifest).contains("value: http://pkb-service", "value: http://bio-compass-auth", "secretKeyRef:")
                .doesNotContain("private-password", "private-service-token", "KUBECONFIG", "hostPort", "LoadBalancer", "NodePort");
    }

    private void execute(FakeKubernetes kube, Duration timeout) throws Exception {
        RunClusterE2e.execute(environment, kube, TEMPLATE, reports, timeout);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    private static String logs(String name, String xml) {
        return "PKB_E2E_REPORT_BEGIN:" + name + "\n"
                + Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)) + "\nPKB_E2E_REPORT_END\n";
    }

    private static final class FakeKubernetes implements RunClusterE2e.Commands {
        final List<List<String>> calls = new ArrayList<>();
        final List<String> inputs = new ArrayList<>();
        final List<Duration> waitTimeouts = new ArrayList<>();
        String state = "Complete";
        boolean failSecret;
        boolean failCleanup;
        boolean failWait;
        String logs = logs("TEST-junit-jupiter.xml",
                "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");

        @Override
        public RunClusterE2e.Result run(List<String> command, String input, Duration timeout) {
            calls.add(command);
            inputs.add(input);
            if (command.contains("wait")) {
                waitTimeouts.add(timeout);
                if (failWait) {
                    return new RunClusterE2e.Result(1, "watch timed out or connection lost");
                }
                return new RunClusterE2e.Result(0, command.contains("--for=condition=Complete") ? "Complete" : state);
            }
            if (input.contains("\"kind\":\"Secret\"") && failSecret) {
                return new RunClusterE2e.Result(1, "Invalid request: private-password");
            }
            if (command.contains("delete") && failCleanup) {
                return new RunClusterE2e.Result(1, "cleanup denied");
            }
            var text = String.join(" ", command);
            var output = text.contains(".metadata.uid") ? "00000000-0000-0000-0000-000000000000"
                    : command.contains("logs") ? logs : "ok";
            return new RunClusterE2e.Result(0, output);
        }
    }
}
