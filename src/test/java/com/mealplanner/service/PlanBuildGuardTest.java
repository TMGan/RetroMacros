package com.mealplanner.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanBuildGuardTest {

    @Test
    void oneBuildAtATimePerClient() {
        PlanBuildGuard guard = new PlanBuildGuard();
        assertThat(guard.tryStart(1L)).isTrue();
        assertThat(guard.tryStart(1L)).isFalse();
        assertThat(guard.isBuilding(1L)).isTrue();
        // Other clients aren't affected.
        assertThat(guard.tryStart(2L)).isTrue();

        guard.finish(1L);
        assertThat(guard.isBuilding(1L)).isFalse();
        assertThat(guard.tryStart(1L)).isTrue();
    }
}
