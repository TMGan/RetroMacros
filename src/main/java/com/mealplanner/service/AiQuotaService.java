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

    public enum Kind { PLAN, SWAP, ESTIMATE }

    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }

    private final AiUsageRepository aiUsageRepository;
    private final int plansPerDay;
    private final int swapsPerDay;
    private final int estimatesPerDay;

    public AiQuotaService(AiUsageRepository aiUsageRepository,
                          @Value("${ai.limit.plans-per-day:3}") int plansPerDay,
                          @Value("${ai.limit.swaps-per-day:15}") int swapsPerDay,
                          @Value("${ai.limit.estimates-per-day:40}") int estimatesPerDay) {
        this.aiUsageRepository = aiUsageRepository;
        this.plansPerDay = plansPerDay;
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
        usage.setRequestCount(usage.getRequestCount() + 1);
        aiUsageRepository.save(usage);
    }

    private int limitFor(Kind kind) {
        switch (kind) {
            case PLAN: return plansPerDay;
            case SWAP: return swapsPerDay;
            default: return estimatesPerDay;
        }
    }

    private String label(Kind kind) {
        switch (kind) {
            case PLAN: return "meal plans";
            case SWAP: return "meal swaps";
            default: return "macro estimates";
        }
    }
}
