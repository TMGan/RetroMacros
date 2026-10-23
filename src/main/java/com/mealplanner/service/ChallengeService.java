package com.mealplanner.service;

import com.mealplanner.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The coaching challenge: its dates, which week it is, and whether a client's access has
 * ended. After access ends a client keeps read-only access to everything they logged.
 */
@Service
public class ChallengeService {

    /** What each week focuses on. One habit at a time, nothing about how fast anyone should go. */
    static final List<String[]> WEEKLY_FOCUS = List.of(
            new String[]{"Get set up", "Make your food plan, log what you eat each day, and do your first check-in. Don't worry about being perfect."},
            new String[]{"Protein at every meal", "Try to include a protein food at breakfast, lunch and dinner. A protein shake counts."},
            new String[]{"Vegetables with every meal", "Add a vegetable to each main meal. Frozen and pre-cut count too."},
            new String[]{"Water and sleep", "Drink your water target and aim for a regular bedtime. Both make the rest easier."},
            new String[]{"Keep it going", "Keep logging and checking in, even on busy days. Rough estimates are fine."},
            new String[]{"Finish strong", "Do a final check-in and, if you like, take your measurements to see how far you've come."});

    private final String name;
    private final LocalDate start;
    private final int weeks;
    private final LocalDate accessUntil;

    public ChallengeService(@Value("${challenge.name:6-Week Challenge}") String name,
                            @Value("${challenge.start:2026-10-26}") String start,
                            @Value("${challenge.weeks:6}") int weeks,
                            @Value("${challenge.access-until:2026-12-13}") String accessUntil) {
        this.name = name;
        this.start = LocalDate.parse(start);
        this.weeks = weeks;
        this.accessUntil = LocalDate.parse(accessUntil);
    }

    public String getName() { return name; }
    public LocalDate getStart() { return start; }
    public int getWeeks() { return weeks; }

    /** The last day of the challenge itself (Sunday of the final week). */
    public LocalDate getEnd() {
        return start.plusWeeks(weeks).minusDays(1);
    }

    /** The client's last day of full access: their own date if the coach extended it, else the default. */
    public LocalDate accessEndFor(User user) {
        return user != null && user.getAccessUntil() != null ? user.getAccessUntil() : accessUntil;
    }

    /** True once a client's access has ended. The coach and the shared demo never expire. */
    public boolean isReadOnly(User user, LocalDate today) {
        if (user == null || user.isAdmin() || DemoAccountService.DEMO_EMAIL.equalsIgnoreCase(user.getEmail())) return false;
        return today.isAfter(accessEndFor(user));
    }

    /** 0 before it starts, 1 to {@code weeks} while it runs, weeks + 1 once it's over. */
    public int weekOf(LocalDate today) {
        if (today.isBefore(start)) return 0;
        long week = ChronoUnit.DAYS.between(start, today) / 7 + 1;
        return (int) Math.min(week, weeks + 1);
    }

    /** Monday of the week containing {@code today}: plan limits count from here. */
    public static LocalDate weekStart(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Everything the Challenge page and banners need. */
    public Map<String, Object> summary(User user, LocalDate today) {
        int week = weekOf(today);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("start", start);
        s.put("end", getEnd());
        s.put("weeks", weeks);
        s.put("week", week);
        s.put("notStarted", week == 0);
        s.put("running", week >= 1 && week <= weeks);
        s.put("finished", week > weeks);
        s.put("daysToStart", week == 0 ? ChronoUnit.DAYS.between(today, start) : 0);
        s.put("daysLeft", week >= 1 && week <= weeks ? ChronoUnit.DAYS.between(today, getEnd()) : 0);
        s.put("accessEnd", accessEndFor(user));
        s.put("readOnly", isReadOnly(user, today));
        int focusWeek = Math.max(1, Math.min(week, weeks));
        s.put("focusTitle", WEEKLY_FOCUS.get(Math.min(focusWeek, WEEKLY_FOCUS.size()) - 1)[0]);
        s.put("focusText", WEEKLY_FOCUS.get(Math.min(focusWeek, WEEKLY_FOCUS.size()) - 1)[1]);
        s.put("allWeeks", WEEKLY_FOCUS);
        return s;
    }
}
