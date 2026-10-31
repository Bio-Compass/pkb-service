package com.biocompass.pkb.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeployChangeClassifierTest {
    @TempDir
    Path directory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes")
    void classifiesTheActualHelmDiffAndProducesReviewArtifacts(
            String name, String diff, int status, boolean reviewRequired, String reviewContent) throws Exception {
        var diffFile = directory.resolve("helm-diff.txt");
        var output = directory.resolve("github-output.txt");
        var summary = directory.resolve("github-summary.md");
        var log = directory.resolve("classifier.log");
        Files.writeString(diffFile, diff);
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                ".github/scripts/ClassifyDeployChange.java",
                "--helm-diff", diffFile.toString(), "--diff-status", String.valueOf(status),
                "--output-dir", directory.toString()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("GITHUB_OUTPUT", output.toString());
        builder.environment().put("GITHUB_STEP_SUMMARY", summary.toString());
        var process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("Classifier completes: %s", name).isTrue();
            assertThat(process.exitValue()).as("Classifier succeeds: %s; %s", name, Files.readString(log)).isZero();
            var properties = new Properties();
            try (var input = Files.newBufferedReader(output)) {
                properties.load(input);
            }
            assertThat(properties.getProperty("image-only")).isEqualTo(String.valueOf(!reviewRequired));
            assertThat(properties.getProperty("requires-approval")).isEqualTo(String.valueOf(reviewRequired));
            assertThat(properties.getProperty("helm-requires-approval")).isEqualTo(String.valueOf(reviewRequired));
            var report = directory.resolve("manual-approval-review.md");
            assertThat(Files.exists(report)).isEqualTo(reviewRequired);
            if (reviewRequired) {
                var text = Files.readString(report);
                assertThat(text).contains("### Manual approval review", "#### Full Helm diff", reviewContent);
                assertThat(Files.readString(summary)).isEqualTo(text);
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }

    private static Stream<Arguments> changes() {
        var imageChange = """
                testing, pkb-service, Deployment (apps) has changed:
                -        image: ghcr.io/bio-compass/pkb-service:main-old
                +        image: ghcr.io/bio-compass/pkb-service:main-new
                """;
        return Stream.of(
                Arguments.of("image-only", imageChange, 2, false, ""),
                Arguments.of("non-image", imageChange + "-        name: OLD_VALUE\n+        name: NEW_VALUE\n",
                        2, true, "NEW_VALUE"),
                Arguments.of("database credential", """
                        testing, pkb-service, Secret (v1) has changed:
                        -  PKB_DATASOURCE_PASSWORD: old-redacted
                        +  PKB_DATASOURCE_PASSWORD: new-redacted
                        """, 2, true, "PKB_DATASOURCE_PASSWORD"),
                Arguments.of("suppressed secret", """
                        testing, pkb-service, Secret (v1) has changed:
                          Secret data suppressed
                        """, 2, true, "Secret (v1) has changed"),
                Arguments.of("no changes", "", 0, false, ""));
    }
}
