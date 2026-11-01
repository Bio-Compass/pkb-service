package com.biocompass.pkb.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class DeploymentWorkflowTest {
    private final Map<String, Object> ci = workflow("ci.yml");
    private final Map<String, Object> deploy = workflow("deploy-environment.yml");

    @Test
    void requiresSuccessfulTestingDeploymentAndE2eBeforeProduction() {
        assertThat(job(ci, "deploy-testing").get("needs")).isEqualTo("publish-image");
        assertThat(job(ci, "e2e-testing").get("needs")).isEqualTo("deploy-testing");
        assertThat(job(ci, "deploy-prod").get("needs"))
                .isEqualTo(List.of("publish-image", "e2e-testing"));
        assertThat(job(ci, "deploy-prod")).doesNotContainKey("if");
        assertThat(job(ci, "e2e-testing").get("if").toString())
                .contains("needs.deploy-testing.result == 'success'", "!cancelled()");
    }

    @Test
    void promotesTheSameImmutableImageAndHelmRevisionToBothNamespaces() {
        for (var environment : List.of("testing", "prod")) {
            var inputs = map(job(ci, "deploy-" + environment), "with");
            assertThat(inputs).containsEntry("environment", environment)
                    .containsEntry("image-ref", "${{ needs.publish-image.outputs.image-ref }}")
                    .containsEntry("helm-revision", "${{ needs.publish-image.outputs.helm-revision }}");
        }
        assertThat(job(ci, "e2e-testing")).containsEntry("environment", "testing");
    }

    @Test
    void manualVerificationUsesClusterJobWithoutPublishingOrDeployingTheApplication() {
        assertThat(job(ci, "deploy-testing").get("if"))
                .isEqualTo("github.event_name == 'push' && github.ref == 'refs/heads/main'");
        assertThat(job(ci, "publish-image").get("if").toString())
                .contains("github.event_name == 'workflow_dispatch'", "!inputs.verify-testing");
        assertThat(job(ci, "e2e-testing").get("if").toString())
                .contains("github.event_name == 'workflow_dispatch' && inputs.verify-testing");
        assertThat(commands(job(ci, "e2e-testing")))
                .contains("./gradlew prepareE2eRunner --no-daemon", "java src/ci/java/com/biocompass/pkb/ci/RunClusterE2e.java")
                .doesNotContain("port-forward");
    }

    @Test
    void requiresProtectedApprovalOnlyForProductionAndKeepsTestingAutomatic() {
        var approve = job(deploy, "approve");
        assertThat(approve.get("if")).isEqualTo("inputs.environment == 'prod'");
        assertThat(map(approve, "environment").get("name"))
                .isEqualTo("${{ inputs.environment }}-manual-approval");
        assertThat(commands(approve)).contains("required_reviewers", "test \"${reviewers}\" -gt 0");
        var apply = job(deploy, "deploy");
        assertThat(apply.get("needs")).isEqualTo(List.of("plan", "approve"));
        assertThat(apply.get("if").toString())
                .contains("needs.plan.result == 'success'", "!cancelled()",
                        "needs.approve.result == 'success'",
                        "inputs.environment == 'testing' && needs.approve.result == 'skipped'")
                .doesNotContain("requires-approval");
    }

    @Test
    void usesRenderedHelmDiffAndOwnedRolloutWithoutReplacingSecrets() {
        assertThat(commands(job(deploy, "plan")))
                .contains("helm diff upgrade", "--suppress-secrets", "java .github/scripts/ClassifyDeployChange.java --helm-diff helm-diff.txt")
                .doesNotContain("--changed-files");
        assertThat(steps(job(deploy, "plan"))).anySatisfy(step -> {
            assertThat(step).containsEntry("uses", "actions/setup-java@v5");
            assertThat(map(step, "with")).containsEntry("java-version", "25");
        });
        assertThat(commands(job(deploy, "deploy")))
                .contains("sh helm-source/scripts/deploy.sh")
                .doesNotContain("secrets.stringData");
        assertThat(steps(job(deploy, "deploy"))).anySatisfy(step -> {
            assertThat(step).containsEntry("uses", "azure/setup-helm@v4");
            assertThat(map(step, "with")).containsEntry("version", "v4.3.1");
        });
    }

    @Test
    void passesTheConfiguredHelmRepositoryIntoTheCompositeAction() {
        var action = yaml(Path.of(".github/actions/prepare-deploy/action.yml"));
        assertThat(map(map(action, "inputs"), "helm-repository")).containsEntry("required", true);
        assertThat(steps(map(action, "runs"))).anySatisfy(step -> {
            assertThat(step).containsEntry("uses", "actions/checkout@v6");
            assertThat(map(step, "with"))
                    .containsEntry("repository", "${{ inputs.helm-repository }}")
                    .containsEntry("ref", "${{ inputs.helm-revision }}");
        });
        var repository = map(ci, "env").get("HELM_VALUES_REPOSITORY");
        for (var phase : List.of("plan", "deploy")) {
            assertThat(steps(job(deploy, phase))).anySatisfy(step -> {
                assertThat(step).containsEntry("uses", "./.github/actions/prepare-deploy");
                assertThat(map(step, "with")).containsEntry("helm-repository", repository);
            });
        }
    }

    @Test
    void neverCancelsAnInFlightMainPromotion() {
        assertThat(map(ci, "concurrency").get("cancel-in-progress"))
                .isEqualTo("${{ github.ref != 'refs/heads/main' }}");
    }

    private static Map<String, Object> workflow(String file) {
        return yaml(Path.of(".github/workflows", file));
    }

    private static Map<String, Object> yaml(Path file) {
        try (var input = Files.newBufferedReader(file)) {
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read CI metadata " + file, exception);
        }
    }

    private static Map<String, Object> job(Map<String, Object> workflow, String name) {
        return map(map(workflow, "jobs"), name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        assertThat(parent.get(key)).as("YAML mapping %s", key).isInstanceOf(Map.class);
        return (Map<String, Object>) parent.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> job) {
        return (List<Map<String, Object>>) job.get("steps");
    }

    private static String commands(Map<String, Object> job) {
        return String.join("\n", steps(job).stream().map(step -> step.getOrDefault("run", "").toString()).toList());
    }
}
