package com.biocompass.pkb.ci;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Runs a bounded testing-only Job; the test pod never receives Kubernetes credentials. */
public final class RunClusterE2e {
    private static final String BEGIN = "PKB_E2E_REPORT_BEGIN:";
    private static final String END = "PKB_E2E_REPORT_END";

    public static void main(String[] args) throws Exception {
        execute(System.getenv(), RunClusterE2e::process,
                Path.of("deploy/kubernetes/e2e/job.yaml"), Path.of("build/reports/cluster-e2e"), Duration.ofMinutes(6));
    }

    static void execute(Map<String, String> environment, Commands commands, Path template, Path reports,
                        Duration timeout) throws Exception {
        var job = jobName(environment);
        var manifest = render(Files.readString(template), environment);
        var username = required(environment, "PKB_E2E_USERNAME");
        var password = required(environment, "PKB_E2E_PASSWORD");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("job.yaml"), manifest);
        Exception failure = null;
        boolean created = false;
        boolean complete = false;
        try {
            require(commands.run(kubectl("create", "-f", "-"), manifest), "Create testing E2E Job");
            created = true;
            var uid = require(commands.run(kubectl("get", "job", job, "-o", "jsonpath={.metadata.uid}"), ""),
                    "Read Job identity").strip();
            if (!uid.matches("[a-f0-9-]{36}")) {
                throw new IllegalStateException("Invalid Kubernetes Job identity");
            }
            // Values travel through stdin, never command arguments, files, or diagnostics.
            var secret = """
                    {"apiVersion":"v1","kind":"Secret","metadata":{"name":"%s-credentials","namespace":"testing",
                    "ownerReferences":[{"apiVersion":"batch/v1","kind":"Job","name":"%s","uid":"%s","blockOwnerDeletion":true}]},
                    "type":"Opaque","data":{"username":"%s","password":"%s"}}
                    """.formatted(job, job, uid, encode(username), encode(password));
            require(commands.run(kubectl("create", "-f", "-"), secret), "Create temporary E2E credentials");
            var image = require(commands.run(kubectl("get", "deployment", "pkb-service", "-o",
                    "jsonpath={.spec.template.spec.containers[?(@.name==\"pkb-service\")].image}"), ""),
                    "Read tested PKB image");
            Files.writeString(reports.resolve("tested-image.txt"), image + "\n");
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                var state = require(commands.run(kubectl("get", "job", job, "-o",
                        "jsonpath={.status.conditions[?(@.status==\"True\")].type}"), ""), "Read E2E Job status");
                if (state.contains("Failed") || state.contains("FailureTarget")) {
                    throw new IllegalStateException("Testing E2E Job failed; see job.log");
                }
                if (state.contains("Complete")) {
                    complete = true;
                    break;
                }
                Thread.sleep(1000);
            }
            if (!complete) {
                throw new IllegalStateException("Testing E2E Job timed out");
            }
        } catch (Exception exception) {
            failure = exception;
        } finally {
            if (created) {
                try {
                    var logs = commands.run(kubectl("logs", "job/" + job, "--container=tests",
                            "--pod-running-timeout=5s"), "");
                    var safeLogs = logs.output().replace(password, "[REDACTED]").replace(username, "[REDACTED]");
                    Files.writeString(reports.resolve("job.log"), safeLogs);
                    var count = collectReports(logs.output(), reports, complete);
                    if (complete && (logs.exitCode() != 0 || count == 0)) {
                        throw new IllegalStateException("Testing Job did not return JUnit reports");
                    }
                } catch (Exception exception) {
                    failure = combine(failure, exception);
                }
                try {
                    // Foreground deletion also removes the Job-owned fixture Secret and pods.
                    require(commands.run(kubectl("delete", "job", job, "--ignore-not-found",
                            "--cascade=foreground", "--wait=true", "--timeout=30s"), ""), "Clean up E2E Job");
                } catch (Exception exception) {
                    failure = combine(failure, exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        System.out.println("Testing in-cluster Java E2E passed; Job and temporary credentials cleaned up.");
    }

    static String render(String template, Map<String, String> environment) {
        var job = jobName(environment);
        var image = required(environment, "PKB_E2E_RUNNER_IMAGE");
        if (!image.matches("ghcr\\.io/bio-compass/pkb-service-e2e@sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("E2E runner must use its immutable CI image digest");
        }
        var revision = required(environment, "GITHUB_SHA");
        if (!revision.matches("[a-f0-9]{40}")) {
            throw new IllegalArgumentException("E2E runner requires the exact source revision");
        }
        return template.replace("${JOB_NAME}", job).replace("${RUNNER_IMAGE}", image)
                .replace("${SOURCE_REVISION}", revision);
    }

    private static String jobName(Map<String, String> environment) {
        if (!"testing".equals(environment.get("PKB_E2E_NAMESPACE"))) {
            throw new IllegalArgumentException("Cluster E2E must target namespace testing");
        }
        var run = required(environment, "GITHUB_RUN_ID");
        var attempt = required(environment, "GITHUB_RUN_ATTEMPT");
        if (!run.matches("[0-9]{1,20}") || !attempt.matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("Invalid CI run identity");
        }
        return "pkb-e2e-" + run + "-" + attempt;
    }

    static int collectReports(String logs, Path directory, boolean requireSuccess) throws Exception {
        String filename = null;
        var encoded = new StringBuilder();
        int reports = 0;
        int tests = 0;
        for (var line : logs.lines().toList()) {
            if (line.startsWith(BEGIN)) {
                if (filename != null) {
                    throw new IllegalStateException("Nested JUnit report in Job logs");
                }
                filename = line.substring(BEGIN.length());
                if (!filename.matches("TEST-[A-Za-z0-9_.-]+\\.xml")) {
                    throw new IllegalStateException("Invalid JUnit report filename");
                }
                encoded.setLength(0);
            } else if (line.equals(END)) {
                if (filename == null) {
                    throw new IllegalStateException("Unexpected JUnit report terminator");
                }
                var bytes = Base64.getDecoder().decode(encoded.toString());
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
                if (!root.getTagName().equals("testsuite")) {
                    throw new IllegalStateException("Unexpected JUnit report format");
                }
                tests += Integer.parseInt(root.getAttribute("tests"));
                if (requireSuccess) {
                    for (var attribute : List.of("failures", "errors", "skipped")) {
                        if (Integer.parseInt(root.getAttribute(attribute)) != 0) {
                            throw new IllegalStateException("JUnit report contains failed, errored or skipped tests");
                        }
                    }
                }
                Files.write(directory.resolve(filename), bytes);
                reports++;
                filename = null;
            } else if (filename != null) {
                encoded.append(line.strip());
                if (encoded.length() > 2_000_000) {
                    throw new IllegalStateException("JUnit report exceeds expected size");
                }
            }
        }
        if (filename != null || (requireSuccess && tests == 0)) {
            throw new IllegalStateException("Incomplete or empty JUnit results");
        }
        return reports;
    }

    private static List<String> kubectl(String... arguments) {
        var command = new ArrayList<>(List.of("kubectl", "--namespace=testing", "--request-timeout=15s"));
        command.addAll(List.of(arguments));
        return command;
    }

    private static Result process(List<String> command, String input) throws Exception {
        var output = Files.createTempFile("pkb-e2e-kubectl-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Kubernetes operation timed out");
            }
            return new Result(process.exitValue(), Files.readString(output));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(output);
        }
    }

    private static String require(Result result, String operation) {
        if (result.exitCode() != 0) {
            // kubectl diagnostics for Secret operations can contain submitted data.
            throw new IllegalStateException(operation + " failed (kubectl exit " + result.exitCode() + ")");
        }
        return result.output();
    }

    private static Exception combine(Exception first, Exception next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String required(Map<String, String> environment, String key) {
        var value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cluster E2E requires " + key);
        }
        return value;
    }

    @FunctionalInterface
    interface Commands {
        Result run(List<String> command, String input) throws Exception;
    }

    record Result(int exitCode, String output) {}
}
