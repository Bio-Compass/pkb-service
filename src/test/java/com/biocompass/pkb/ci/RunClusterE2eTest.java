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
            "PKB_E2E_USERNAME", "fixture@example.test", "PKB_E2E_PASSWORD", "private-password"));
    @TempDir
    Path reports;

    @Test
    void createsPrivateBoundedJobAndCollectsResultsBeforeForegroundCleanup() throws Exception {
        var kube = new FakeKubernetes();
        execute(kube, Duration.ofSeconds(5));
        assertThat(Files.readString(reports.resolve("TEST-junit-jupiter.xml"))).contains("tests=\"1\"");
        assertThat(kube.inputs).anySatisfy(input -> assertThat(input)
                .contains("\"kind\":\"Secret\"", "\"ownerReferences\"", "00000000-0000-0000-0000-000000000000")
                .doesNotContain("private-password"));
        assertThat(kube.calls).allSatisfy(call -> assertThat(call).contains("--namespace=testing")
                .doesNotContain("exec", "port-forward", "private-password"));
        assertThat(kube.calls.getLast()).contains("delete", "job", "--cascade=foreground");
    }

    @Test
    void failsAndCleansUpWhenTheJobFailsOrTimesOut() throws Exception {
        var failedKube = new FakeKubernetes();
        failedKube.state = "Failed";
        assertThatThrownBy(() -> execute(failedKube, Duration.ofSeconds(5))).hasMessageContaining("Job failed");
        assertThat(failedKube.calls.getLast()).contains("delete");
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
                .doesNotContain("private-password", "KUBECONFIG", "hostPort", "LoadBalancer", "NodePort");
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
        String state = "Complete";
        boolean failSecret;
        boolean failCleanup;
        String logs = logs("TEST-junit-jupiter.xml",
                "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");

        @Override
        public RunClusterE2e.Result run(List<String> command, String input) {
            calls.add(command);
            inputs.add(input);
            if (input.contains("\"kind\":\"Secret\"") && failSecret) {
                return new RunClusterE2e.Result(1, "Invalid request: private-password");
            }
            if (command.contains("delete") && failCleanup) {
                return new RunClusterE2e.Result(1, "cleanup denied");
            }
            var text = String.join(" ", command);
            var output = text.contains(".metadata.uid") ? "00000000-0000-0000-0000-000000000000"
                    : text.contains(".status.conditions") ? state
                    : command.contains("logs") ? logs : "ok";
            return new RunClusterE2e.Result(0, output);
        }
    }
}
