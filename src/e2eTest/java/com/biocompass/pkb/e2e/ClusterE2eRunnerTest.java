package com.biocompass.pkb.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

class ClusterE2eRunnerTest {
    @Test
    void requiresEveryDiscoveredTestToSucceed() {
        var result = mock(TestExecutionSummary.class);
        assertThat(ClusterE2eRunner.successful(result)).isFalse();
        when(result.getTestsFoundCount()).thenReturn(6L);
        when(result.getTestsSucceededCount()).thenReturn(6L);
        assertThat(ClusterE2eRunner.successful(result)).isTrue();
        when(result.getTestsSkippedCount()).thenReturn(1L);
        assertThat(ClusterE2eRunner.successful(result)).isFalse();
        when(result.getTestsSkippedCount()).thenReturn(0L);
        when(result.getContainersFailedCount()).thenReturn(1L);
        assertThat(ClusterE2eRunner.successful(result)).isFalse();
    }
}
