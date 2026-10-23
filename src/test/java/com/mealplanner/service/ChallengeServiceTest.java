package com.mealplanner.service;

import com.mealplanner.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeServiceTest {

    private final ChallengeService challenge = new ChallengeService("6-Week Challenge", "2026-10-26", 6, "2026-12-13");

    private static User client() {
        return new User("c@example.com", "Client", "g-1");
    }

    @Test
    void weeksCountFromTheStartMonday() {
        assertThat(challenge.weekOf(LocalDate.of(2026, 10, 23))).isEqualTo(0);
        assertThat(challenge.weekOf(LocalDate.of(2026, 10, 26))).isEqualTo(1);
        assertThat(challenge.weekOf(LocalDate.of(2026, 11, 1))).isEqualTo(1);
        assertThat(challenge.weekOf(LocalDate.of(2026, 11, 2))).isEqualTo(2);
        assertThat(challenge.weekOf(LocalDate.of(2026, 12, 6))).isEqualTo(6);
        assertThat(challenge.weekOf(LocalDate.of(2026, 12, 7))).isEqualTo(7);
        assertThat(challenge.getEnd()).isEqualTo(LocalDate.of(2026, 12, 6));
    }

    @Test
    void accessRunsThroughTheGraceWeekThenGoesReadOnly() {
        assertThat(challenge.isReadOnly(client(), LocalDate.of(2026, 10, 23))).isFalse();
        assertThat(challenge.isReadOnly(client(), LocalDate.of(2026, 12, 13))).isFalse();
        assertThat(challenge.isReadOnly(client(), LocalDate.of(2026, 12, 14))).isTrue();
    }

    @Test
    void coachExtensionAndExemptionsWin() {
        User extended = client();
        extended.setAccessUntil(LocalDate.of(2027, 1, 31));
        assertThat(challenge.isReadOnly(extended, LocalDate.of(2027, 1, 1))).isFalse();

        User coach = client();
        coach.setAdmin(true);
        assertThat(challenge.isReadOnly(coach, LocalDate.of(2030, 1, 1))).isFalse();

        User demo = new User(DemoAccountService.DEMO_EMAIL, "Demo", "demo");
        assertThat(challenge.isReadOnly(demo, LocalDate.of(2030, 1, 1))).isFalse();
    }

    @Test
    void planWeeksStartOnMonday() {
        assertThat(ChallengeService.weekStart(LocalDate.of(2026, 10, 28))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(ChallengeService.weekStart(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(ChallengeService.weekStart(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void summaryCoversBeforeDuringAndAfter() {
        Map<String, Object> before = challenge.summary(client(), LocalDate.of(2026, 10, 23));
        assertThat(before.get("notStarted")).isEqualTo(true);
        assertThat(before.get("daysToStart")).isEqualTo(3L);
        assertThat(before.get("focusTitle")).isEqualTo("Get set up");

        Map<String, Object> during = challenge.summary(client(), LocalDate.of(2026, 11, 3));
        assertThat(during.get("running")).isEqualTo(true);
        assertThat(during.get("week")).isEqualTo(2);
        assertThat(during.get("daysLeft")).isEqualTo(33L);

        Map<String, Object> after = challenge.summary(client(), LocalDate.of(2026, 12, 20));
        assertThat(after.get("finished")).isEqualTo(true);
        assertThat(after.get("readOnly")).isEqualTo(true);
    }
}
