package com.biocompass.pkb.ci;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
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
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Node;

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
        var serviceName = required(environment, "PKB_E2E_SERVICE_NAME");
        var serviceToken = required(environment, "PKB_E2E_SERVICE_TOKEN");
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
                    "type":"Opaque","data":{"username":"%s","password":"%s","service-name":"%s","service-token":"%s"}}
                    """.formatted(job, job, uid, encode(username), encode(password), encode(serviceName), encode(serviceToken));
            require(commands.run(kubectl("create", "-f", "-"), secret), "Create temporary E2E credentials");
            var image = require(commands.run(kubectl("get", "deployment", "pkb-service", "-o",
                    "jsonpath={.spec.template.spec.containers[?(@.name==\"pkb-service\")].image}"), ""),
                    "Read tested PKB image");
            Files.writeString(reports.resolve("tested-image.txt"), image + "\n");
            long deadline = System.nanoTime() + timeout.toNanos();
            // kubectl watches API events instead of repeatedly starting status requests.
            var state = waitForJob(commands, job, "jsonpath={.status.conditions}", deadline);
            if (state.contains("Failed") || state.contains("FailureTarget")) {
                throw new IllegalStateException("Testing E2E Job failed; see job.log");
            }
            if (!state.contains("Complete")) {
                if (!state.contains("SuccessCriteriaMet")) {
                    throw new IllegalStateException("Testing E2E Job returned an unexpected condition");
                }
                // SuccessCriteriaMet precedes Complete while Kubernetes terminates the pods.
                waitForJob(commands, job, "condition=Complete", deadline);
            }
            complete = true;
        } catch (Exception exception) {
            failure = exception;
        } finally {
            if (created) {
                try {
                    var logs = commands.run(kubectl("logs", "job/" + job, "--container=tests",
                            "--pod-running-timeout=5s"), "");
                    var safeLogs = safeJobLogs(logs.output(), username, password, serviceName, serviceToken);
                    Files.writeString(reports.resolve("job.log"), safeLogs);
                    var count = collectReports(logs.output(), reports, complete, username, password, serviceName, serviceToken);
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

    private static String waitForJob(Commands commands, String job, String condition, long deadline)
            throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new IllegalStateException("Testing E2E Job timed out");
        }
        var wait = Duration.ofNanos(remaining);
        var command = List.of("kubectl", "--namespace=testing", "--request-timeout=0", "wait", "job/" + job,
                "--for=" + condition, "--timeout=" + wait.toMillis() + "ms", "-o",
                "jsonpath={.status.conditions[?(@.status==\"True\")].type}");
        return require(commands.run(command, "", wait.plusSeconds(15)), "Wait for E2E Job");
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

    static int collectReports(String logs, Path directory, boolean requireSuccess, String... credentials) throws Exception {
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
                // Coalesce adjacent text/CDATA before redacting values split by XML serialization.
                var document = root.getOwnerDocument();
                document.getDomConfig().setParameter("cdata-sections", false);
                document.normalizeDocument();
                redactNode(root, credentials);
                var transformerFactory = TransformerFactory.newInstance();
                transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
                var output = new StringWriter();
                transformerFactory.newTransformer().transform(new DOMSource(root), new StreamResult(output));
                Files.writeString(directory.resolve(filename), output.toString(), StandardCharsets.UTF_8);
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

    private static String safeJobLogs(String logs, String... credentials) {
        // Decoded reports are saved separately; never duplicate their encoded contents in logs.
        var output = new StringBuilder();
        boolean report = false;
        var parts = logs.splitWithDelimiters("\\R", -1);
        for (int i = 0; i < parts.length; i += 2) {
            var line = parts[i];
            if (line.startsWith(BEGIN)) report = true;
            else if (line.equals(END)) report = false;
            else if (!report) {
                output.append(line);
                if (i + 1 < parts.length) output.append(parts[i + 1]);
            }
        }
        return redact(output.toString(), credentials);
    }

    private static void redactNode(Node node, String... credentials) {
        if (node.getNodeValue() != null) node.setNodeValue(redact(node.getNodeValue(), credentials));
        var attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) redactNode(attributes.item(i), credentials);
        }
        for (var child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            redactNode(child, credentials);
        }
    }

    private static String redact(String value, String... credentials) {
        var ordered = java.util.Arrays.stream(credentials).filter(credential -> !credential.isEmpty())
                .sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList();
        for (var credential : ordered) {
            value = value.replace(credential, "[REDACTED]").replace(encode(credential), "[REDACTED]");
        }
        return value;
    }

    private static List<String> kubectl(String... arguments) {
        var command = new ArrayList<>(List.of("kubectl", "--namespace=testing", "--request-timeout=15s"));
        command.addAll(List.of(arguments));
        return command;
    }

    private static Result process(List<String> command, String input, Duration timeout) throws Exception {
        var output = Files.createTempFile("pkb-e2e-kubectl-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
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
        Result run(List<String> command, String input, Duration timeout) throws Exception;

        default Result run(List<String> command, String input) throws Exception {
            return run(command, input, Duration.ofSeconds(45));
        }
    }

    record Result(int exitCode, String output) {}
}
