package com.mealplanner.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientTest {

    private AiClient clientWith(MockEnvironment env, String apiKey) {
        return new AiClient(null, env, apiKey, 1000, "claude-sonnet-5-5", "claude-haiku-5-5", "medium", "low");
    }

    @Test
    void costUsesListPricesPerMillionTokens() {
        AiClient client = clientWith(new MockEnvironment(), "");

        // A typical plan: 1,500 tokens in at $2/M plus 6,000 out at $10/M = $0.063
        assertThat(client.costMicros("claude-sonnet-5-5", 1500, 6000)).isEqualTo(63_000);
        // A typical estimate: 300 in at $0.10/M plus 400 out at $0.50/M = $0.00023
        assertThat(client.costMicros("claude-haiku-5-5", 300, 400)).isEqualTo(230);
    }

    @Test
    void pricesCanBeOverriddenInConfig() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("ai.price.claude-sonnet-5-5.input", "3")
                .withProperty("ai.price.claude-sonnet-5-5.output", "15");

        assertThat(clientWith(env, "").costMicros("claude-sonnet-5-5", 1_000_000, 1_000_000)).isEqualTo(18_000_000);
    }

    @Test
    void unknownModelIsRecordedAsZeroCostRatherThanGuessed() {
        assertThat(clientWith(new MockEnvironment(), "").costMicros("some-future-model", 1000, 1000)).isZero();
    }

    @Test
    void withoutAnApiKeyCallsFailWithASafeMessage() {
        AiClient client = clientWith(new MockEnvironment(), "");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.complete(null, AiClient.Kind.ESTIMATE, "two eggs"))
                .isInstanceOf(AiClient.AiException.class)
                .hasMessageContaining("not configured");
    }

    private static com.mealplanner.model.User client(boolean admin) {
        com.mealplanner.model.User u = new com.mealplanner.model.User("c@example.com", "Client", "g-1");
        u.setAdmin(admin);
        return u;
    }

    @Test
    void stopsAClientOverTheirSpendCapBeforeCalling() {
        com.mealplanner.repository.AiCallLogRepository repo = org.mockito.Mockito.mock(com.mealplanner.repository.AiCallLogRepository.class);
        org.mockito.Mockito.when(repo.sumCostMicrosSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        org.mockito.Mockito.when(repo.sumCostMicrosForUserSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(4_000_000L);
        AiClient ai = new AiClient(repo, new MockEnvironment().withProperty("ai.budget.user-30d-usd", "4.00"),
                "test-key", 1000, "claude-sonnet-5-5", "claude-haiku-5-5", "low", "low");

        assertThatThrownBy(() -> ai.complete(client(false), AiClient.Kind.PLAN, "plan"))
                .isInstanceOf(AiClient.AiException.class)
                .hasMessageContaining("AI allowance");
    }

    @Test
    void stopsEveryoneOverTheGlobalCap() {
        com.mealplanner.repository.AiCallLogRepository repo = org.mockito.Mockito.mock(com.mealplanner.repository.AiCallLogRepository.class);
        org.mockito.Mockito.when(repo.sumCostMicrosSince(org.mockito.ArgumentMatchers.any())).thenReturn(150_000_000L);
        AiClient ai = new AiClient(repo, new MockEnvironment(), "test-key", 1000, "claude-sonnet-5-5", "claude-haiku-5-5", "low", "low");

        // The coach is exempt from the per-client cap, but not from the global one.
        assertThatThrownBy(() -> ai.complete(client(true), AiClient.Kind.PLAN, "plan"))
                .isInstanceOf(AiClient.AiException.class)
                .hasMessageContaining("paused");
    }
}
