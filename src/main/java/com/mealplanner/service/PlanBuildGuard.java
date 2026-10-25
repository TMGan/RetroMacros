package com.mealplanner.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One plan at a time per client. A plan takes a few minutes, and a client who refreshes or
 * comes back to the form would otherwise start a second, paid plan while the first is still
 * being written. The app runs as a single server, so an in-memory map is enough.
 */
@Component
public class PlanBuildGuard {

    // Longer than the AI timeout with its one retry, so a stuck entry can't block anyone for long.
    static final Duration STALE_AFTER = Duration.ofMinutes(20);

    private final Map<Long, Instant> building = new ConcurrentHashMap<>();

    /** Marks a plan as building for this client, or returns false if one already is. */
    public boolean tryStart(Long userId) {
        Instant now = Instant.now();
        building.computeIfPresent(userId, (id, started) -> started.isBefore(now.minus(STALE_AFTER)) ? null : started);
        return building.putIfAbsent(userId, now) == null;
    }

    public boolean isBuilding(Long userId) {
        Instant started = building.get(userId);
        return started != null && started.isAfter(Instant.now().minus(STALE_AFTER));
    }

    public void finish(Long userId) {
        building.remove(userId);
    }
}
