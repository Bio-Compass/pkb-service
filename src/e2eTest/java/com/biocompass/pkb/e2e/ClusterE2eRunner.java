package com.biocompass.pkb.e2e;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

public final class ClusterE2eRunner {
    public static void main(String[] args) throws Exception {
        var directory = Path.of("/reports");
        Files.createDirectories(directory);
        var summary = new SummaryGeneratingListener();
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(PkbServiceE2eTest.class))
                .filters(TagFilter.excludeTags("local-fixture")).build();
        try (var launcher = LauncherFactory.openSession()) {
            launcher.getLauncher().execute(request, summary,
                    new LegacyXmlReportGeneratingListener(directory, new PrintWriter(System.out, true)));
        }
        var result = summary.getSummary();
        var output = new PrintWriter(System.out, true);
        result.printTo(output);
        result.printFailuresTo(output);
        try (var files = Files.list(directory)) {
            for (var file : files.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                System.out.println("PKB_E2E_REPORT_BEGIN:" + file.getFileName());
                System.out.println(Base64.getMimeEncoder(120, new byte[]{'\n'}).encodeToString(Files.readAllBytes(file)));
                System.out.println("PKB_E2E_REPORT_END");
            }
        }
        if (!successful(result)) {
            System.exit(1);
        }
    }

    static boolean successful(TestExecutionSummary result) {
        return result.getTestsFoundCount() > 0
                && result.getTestsSucceededCount() == result.getTestsFoundCount()
                && result.getContainersFailedCount() == 0
                && result.getTestsFailedCount() == 0
                && result.getTestsAbortedCount() == 0
                && result.getTestsSkippedCount() == 0;
    }
}
