package com.mealplanner.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.mealplanner.model.AiCallLog;
import com.mealplanner.model.User;
import com.mealplanner.repository.AiCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The one place the app talks to the AI provider. Every call is routed to the
 * right model for its job and written to {@link AiCallLog} with real token
 * counts and cost.
 */
@Service
public class AiClient {

    /** What a call is for. Decides the model, output allowance and effort. */
    public enum Kind {
        // Plans write 3 days of meals with recipes plus the model's own thinking. Most use about 20k,
        // but plans with many rules (diabetes plus GLP-1: 6 eating times, even carbs) sometimes ran past
        // 32k and were cut off, still billed. Only the tokens used are billed, so the higher cap is free
        // for normal plans.
        PLAN(true, 48000), PLAN_REPAIR(true, 32000), SWAP(true, 8000),
        ESTIMATE(false, 2000), SUGGESTION(false, 4000);

        final boolean usesPlanModel;
        // Includes the model's thinking, so it is well above the visible answer size.
        final long maxTokens;

        Kind(boolean usesPlanModel, long maxTokens) {
            this.usesPlanModel = usesPlanModel;
            this.maxTokens = maxTokens;
        }
    }

    /** A failed AI call. The message is safe to show to a user. */
    public static class AiException extends RuntimeException {
        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final AiCallLogRepository aiCallLogRepository;
    private final Environment env;
    private final String planModel;
    private final String logModel;
    private final OutputConfig.Effort planEffort;
    private final OutputConfig.Effort logEffort;
    private final AnthropicClient client;

    public AiClient(AiCallLogRepository aiCallLogRepository,
                    Environment env,
                    @Value("${ai.api.key:}") String apiKey,
                    @Value("${ai.timeout.ms:480000}") long timeoutMs,
                    @Value("${ai.model.plan:claude-sonnet-5-5}") String planModel,
                    @Value("${ai.model.log:claude-haiku-5-5}") String logModel,
                    @Value("${ai.effort.plan:low}") String planEffort,
                    @Value("${ai.effort.log:low}") String logEffort) {
        this.aiCallLogRepository = aiCallLogRepository;
        this.env = env;
        this.planModel = planModel;
        this.logModel = logModel;
        this.planEffort = effort(planEffort);
        this.logEffort = effort(logEffort);
        this.client = (apiKey == null || apiKey.isBlank()) ? null
                // One retry at most: a slow plan retried twice more would be billed up to three times.
                : AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(Duration.ofMillis(timeoutMs)).maxRetries(1).build();
    }

    public boolean isConfigured() {
        return client != null;
    }

    /**
     * Sends one prompt and returns the model's text answer.
     *
     * @param user who the call is for, or null for system work; cost is recorded against them
     * @throws AiException if the call fails, is refused, or is cut off
     */
    public String complete(User user, Kind kind, String prompt) {
        if (client == null) {
            throw new AiException("AI is not configured on this server.", null);
        }
        checkBudget(user);
        String model = kind.usesPlanModel ? planModel : logModel;
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(kind.maxTokens)
                .outputConfig(OutputConfig.builder().effort(kind.usesPlanModel ? planEffort : logEffort).build())
                .addUserMessage(prompt)
                .build();

        long started = System.currentTimeMillis();
        Message message;
        try {
            message = client.messages().create(params);
        } catch (AnthropicServiceException e) {
            String errorType = e.errorType().map(Object::toString).orElse("http_" + e.statusCode());
            record(user, kind, model, 0, 0, false, errorType, started);
            log.warn("AI call failed: kind={} model={} status={} type={}", kind, model, e.statusCode(), errorType);
            throw new AiException("The AI service is unavailable right now. Please try again in a minute.", e);
        } catch (RuntimeException e) {
            record(user, kind, model, 0, 0, false, "client_error", started);
            log.warn("AI call failed before a response: kind={} model={}", kind, model, e);
            throw new AiException("The AI service is unavailable right now. Please try again in a minute.", e);
        }

        long inputTokens = message.usage().inputTokens();
        long outputTokens = message.usage().outputTokens();
        StopReason stopReason = message.stopReason().orElse(null);

        // A refusal or a cut-off answer is still billed, so record it before failing.
        if (StopReason.REFUSAL.equals(stopReason)) {
            record(user, kind, model, inputTokens, outputTokens, false, "refusal", started);
            throw new AiException("The AI could not help with that request.", null);
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            record(user, kind, model, inputTokens, outputTokens, false, "max_tokens", started);
            throw new AiException("The AI answer was cut off. Please try again.", null);
        }

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining());
        record(user, kind, model, inputTokens, outputTokens, true, null, started);
        return text;
    }

    /** Spend caps over the last 30 days, in dollars: per client (the coach is exempt) and for the whole app. */
    public double userBudgetUsd() {
        return env.getProperty("ai.budget.user-30d-usd", Double.class, 4.0);
    }

    public double globalBudgetUsd() {
        return env.getProperty("ai.budget.global-30d-usd", Double.class, 150.0);
    }

    /**
     * Stops a call before it is made once a cap is reached, so one heavy user (or a bug in a
     * retry loop) can't run up the bill. Daily request limits live in {@link AiQuotaService}.
     */
    private void checkBudget(User user) {
        if (aiCallLogRepository == null) return;
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusDays(30);
        if (aiCallLogRepository.sumCostMicrosSince(since) >= globalBudgetUsd() * 1_000_000) {
            log.warn("Global AI budget of ${} per 30 days reached; refusing AI calls", globalBudgetUsd());
            throw new AiException("AI features are paused for a short while. Your coach has been notified. "
                    + "You can still log food by entering the numbers yourself.", null);
        }
        if (user != null && !user.isAdmin()
                && aiCallLogRepository.sumCostMicrosForUserSince(user, since) >= userBudgetUsd() * 1_000_000) {
            log.info("User {} reached the per-user AI budget", user.getId());
            throw new AiException("You've reached your AI allowance for now. It frees up gradually over the next few weeks. "
                    + "You can still log food by entering the numbers yourself, and your coach can help in the meantime.", null);
        }
    }

    private void record(User user, Kind kind, String model, long inputTokens, long outputTokens,
                        boolean success, String errorType, long startedMillis) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setUser(user);
            entry.setKind(kind.name());
            entry.setModel(model);
            entry.setInputTokens(inputTokens);
            entry.setOutputTokens(outputTokens);
            entry.setCostMicros(costMicros(model, inputTokens, outputTokens));
            entry.setSuccess(success);
            entry.setErrorType(errorType);
            entry.setDurationMs(System.currentTimeMillis() - startedMillis);
            aiCallLogRepository.save(entry);
        } catch (RuntimeException e) {
            // Losing a log row must never fail the user's request.
            log.error("Could not record AI usage for kind={} model={}", kind, model, e);
        }
    }

    /**
     * Cost in millionths of a dollar. Tokens times dollars-per-million-tokens is
     * already in that unit. Prices come from ai.price.&lt;model&gt;.input and .output,
     * falling back to the list prices this was written against.
     */
    long costMicros(String model, long inputTokens, long outputTokens) {
        double inputPrice = price(model, "input");
        double outputPrice = price(model, "output");
        return Math.round(inputTokens * inputPrice + outputTokens * outputPrice);
    }

    private double price(String model, String direction) {
        Double configured = env.getProperty("ai.price." + model + "." + direction, Double.class);
        if (configured != null) return configured;
        boolean input = "input".equals(direction);
        if (model.startsWith("claude-haiku-5-5")) return input ? 0.10 : 0.50;
        if (model.startsWith("claude-sonnet-5")) return input ? 2.00 : 10.00;
        if (model.startsWith("claude-opus-5-5")) return input ? 4.00 : 20.00;
        log.warn("No price configured for model {}; its cost will be recorded as 0. Set ai.price.{}.{}", model, model, direction);
        return 0.0;
    }

    private static OutputConfig.Effort effort(String name) {
        switch (name == null ? "" : name.trim().toLowerCase(Locale.ROOT)) {
            case "low": return OutputConfig.Effort.LOW;
            case "high": return OutputConfig.Effort.HIGH;
            default: return OutputConfig.Effort.MEDIUM;
        }
    }
}
