package com.mealplanner.service;

import com.mealplanner.model.AiUsage;
import com.mealplanner.model.User;
import com.mealplanner.repository.AiUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Caps how many AI-backed requests each user can make per day. */
@Service
public class AiQuotaService {

    /** AI kinds cap spend; the rest cap how much any account (including the public demo) can write per day. */
    public enum Kind { PLAN, SWAP, ESTIMATE, FOOD_LOG, SWAP_RECORD, PLAN_SAVE, PLAN_FAILURE }

    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }

    private final AiUsageRepository aiUsageRepository;
    private final int plansPerDay;
    private final int swapsPerDay;
    private final int estimatesPerDay;
    private final int foodLogsPerDay;
    private final int swapRecordsPerDay;
    private final int planSavesPerDay;
    private final int plansPerWeek;
    private final int failedPlansPerDay;

    public AiQuotaService(AiUsageRepository aiUsageRepository,
                          @Value("${ai.limit.plans-per-day:2}") int plansPerDay,
                          @Value("${ai.limit.plans-per-week:2}") int plansPerWeek,
                          @Value("${ai.limit.swaps-per-day:10}") int swapsPerDay,
                          @Value("${ai.limit.estimates-per-day:40}") int estimatesPerDay,
                          @Value("${app.limit.food-logs-per-day:100}") int foodLogsPerDay,
                          @Value("${app.limit.swap-records-per-day:50}") int swapRecordsPerDay,
                          @Value("${app.limit.plan-saves-per-day:20}") int planSavesPerDay,
                          @Value("${ai.limit.failed-plans-per-day:3}") int failedPlansPerDay) {
        this.failedPlansPerDay = failedPlansPerDay;
        this.foodLogsPerDay = foodLogsPerDay;
        this.swapRecordsPerDay = swapRecordsPerDay;
        this.planSavesPerDay = planSavesPerDay;
        this.aiUsageRepository = aiUsageRepository;
        this.plansPerDay = plansPerDay;
        this.plansPerWeek = plansPerWeek;
        this.swapsPerDay = swapsPerDay;
        this.estimatesPerDay = estimatesPerDay;
    }

    /** Counts one request against today's allowance, or throws if it is used up. */
    @Transactional
    public void consume(User user, Kind kind) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AiUsage usage = aiUsageRepository.findByUserAndKindAndUsageDate(user, kind.name(), today)
                .orElseGet(() -> new AiUsage(user, kind.name(), today));
        int limit = limitFor(kind);
        if (usage.getRequestCount() >= limit) {
            throw new QuotaExceededException("You have reached today's limit of " + limit + " "
                    + label(kind) + ". It resets at midnight UTC.");
        }
        // Plans also have a weekly cap, counted Monday to Sunday like the challenge weeks. The coach is exempt.
        if (kind == Kind.PLAN && !user.isAdmin()
                && plansThisWeek(user, today) >= plansPerWeek) {
            throw new QuotaExceededException("You've made your " + plansPerWeek + " food plans for this week. "
                    + "Your plans are under My Food Plan, and you can make a new one on Monday.");
        }
        usage.setRequestCount(usage.getRequestCount() + 1);
        aiUsageRepository.save(usage);
    }

    /** Hands back one use, for a request that failed without producing anything. */
    @Transactional
    public void refund(User user, Kind kind) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        aiUsageRepository.findByUserAndKindAndUsageDate(user, kind.name(), today).ifPresent(usage -> {
            usage.setRequestCount(Math.max(0, usage.getRequestCount() - 1));
            aiUsageRepository.save(usage);
        });
    }

    /**
     * Failed plans still cost real AI spend even though their use is handed back, so after a few
     * failures in a day the client stops retrying until tomorrow. The coach is exempt.
     */
    public boolean tooManyFailedPlans(User user) {
        if (user.isAdmin()) return false;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return aiUsageRepository.findByUserAndKindAndUsageDate(user, Kind.PLAN_FAILURE.name(), today)
                .map(u -> u.getRequestCount() >= failedPlansPerDay)
                .orElse(false);
    }

    @Transactional
    public void recordFailedPlan(User user) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AiUsage usage = aiUsageRepository.findByUserAndKindAndUsageDate(user, Kind.PLAN_FAILURE.name(), today)
                .orElseGet(() -> new AiUsage(user, Kind.PLAN_FAILURE.name(), today));
        usage.setRequestCount(usage.getRequestCount() + 1);
        aiUsageRepository.save(usage);
    }

    public long plansThisWeek(User user, LocalDate today) {
        return aiUsageRepository.sumRequestsSince(user, Kind.PLAN.name(), ChallengeService.weekStart(today));
    }

    public long plansThisWeek(User user) {
        return plansThisWeek(user, LocalDate.now(ZoneOffset.UTC));
    }

    public int getPlansPerWeek() {
        return plansPerWeek;
    }

    private int limitFor(Kind kind) {
        switch (kind) {
            case PLAN: return plansPerDay;
            case SWAP: return swapsPerDay;
            case FOOD_LOG: return foodLogsPerDay;
            case SWAP_RECORD: return swapRecordsPerDay;
            case PLAN_SAVE: return planSavesPerDay;
            case PLAN_FAILURE: return failedPlansPerDay;
            default: return estimatesPerDay;
        }
    }

    private String label(Kind kind) {
        switch (kind) {
            case PLAN: return "meal plans";
            case SWAP: return "meal swaps";
            case FOOD_LOG: return "food log entries";
            case SWAP_RECORD: return "recorded swaps";
            case PLAN_SAVE: return "saved plans";
            case PLAN_FAILURE: return "failed plans";
            default: return "macro estimates";
        }
    }
}
