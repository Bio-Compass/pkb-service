import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** Standalone Java source launcher used by CI; not packaged with the PKB application. */
public class ClassifyDeployChange {
    private static final Pattern SECRET_CHANGE = Pattern.compile(
            ",\\s*Secret\\s*\\([^)]*\\)\\s*(has changed|has been added|has been removed):");
    private static final Pattern DIFF_HEADER = Pattern.compile("^(\\+\\+\\+|---)(\\s|$)");
    private static final Pattern IMAGE = Pattern.compile("\\s*image:\\s.*");

    public static void main(String[] args) throws IOException {
        var options = new HashMap<String, String>();
        var keys = Set.of("--helm-diff", "--diff-status", "--output-dir");
        if (args.length != 6) {
            throw new IllegalArgumentException("Expected --helm-diff FILE --diff-status STATUS --output-dir DIR");
        }
        for (int index = 0; index < args.length; index += 2) {
            if (!keys.contains(args[index]) || options.put(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("Invalid classifier arguments");
            }
        }
        var diff = Files.readString(Path.of(options.get("--helm-diff")));
        var output = Path.of(options.get("--output-dir"));
        Files.createDirectories(output);
        var nonImage = new StringBuilder();
        if (!options.get("--diff-status").equals("0")) {
            for (var line : diff.lines().toList()) {
                if (SECRET_CHANGE.matcher(line).find()) {
                    nonImage.append(line).append('\n');
                } else if (!DIFF_HEADER.matcher(line).find() && (line.startsWith("+") || line.startsWith("-"))
                        && !IMAGE.matcher(line.substring(1)).matches()) {
                    nonImage.append(line).append('\n');
                }
            }
        }
        Files.writeString(output.resolve("non-image-diff.txt"), nonImage);
        var requiresReview = !nonImage.isEmpty();
        appendEnvironmentFile("GITHUB_OUTPUT", "image-only=" + !requiresReview + "\n"
                + "requires-approval=" + requiresReview + "\nhelm-requires-approval=" + requiresReview + "\n");
        if (requiresReview) {
            var report = """
                    ### Manual approval review

                    Review this environment diff before approving production promotion.

                    - Helm diff requires approval: true

                    #### Full Helm diff

                    ```diff
                    %s
                    ```

                    #### Non-image Helm diff

                    The Helm diff includes changes beyond container image updates.

                    ```diff
                    %s
                    ```
                    """.formatted(limit(diff, 60_000), limit(nonImage.toString(), 20_000));
            Files.writeString(output.resolve("manual-approval-review.md"), report);
            appendEnvironmentFile("GITHUB_STEP_SUMMARY", report);
        }
    }

    private static String limit(String value, int characters) {
        return value.substring(0, Math.min(value.length(), characters));
    }

    private static void appendEnvironmentFile(String key, String content) throws IOException {
        var file = System.getenv(key);
        if (file != null && !file.isBlank()) {
            Files.writeString(Path.of(file), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
