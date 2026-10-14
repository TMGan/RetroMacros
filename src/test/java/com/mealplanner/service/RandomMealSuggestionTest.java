package com.mealplanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RandomMealSuggestionTest {

    private final FoodLogService service = new FoodLogService(null, new MockEnvironment().withProperty("ai.mock", "true"));

    /** Wraps model output the way the Messages API returns it. */
    private String apiResponse(String modelText) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "content", List.of(Map.of("type", "text", "text", modelText))));
    }

    @Test
    void offlineSuggestionSaysWhatIsInItAndHowToMakeIt() {
        Map<String, Object> meal = service.generateRandomHealthyMeal(500, true);

        assertThat((String) meal.get("description")).isNotBlank();
        assertThat((List<?>) meal.get("ingredients")).hasSizeGreaterThanOrEqualTo(3);
        assertThat((List<?>) meal.get("steps")).hasSizeGreaterThanOrEqualTo(3);
        assertThat((Integer) meal.get("calories")).isBetween(300, 800);
    }

    @Test
    void parsesAiSuggestionWithQuotesNewlinesAndCodeFences() throws Exception {
        String modelText = "```json\n{\"name\": \"Chicken \\\"Power\\\" Bowl\",\n"
                + " \"ingredients\": [\"6 oz chicken breast\", \"1 cup cooked rice\", \"1 cup broccoli\", \"1 tsp olive oil\"],\n"
                + " \"steps\": [\"Season and cook the chicken.\", \"Steam the broccoli.\", \"Serve over rice.\"],\n"
                + " \"calories\": 520, \"protein\": 52, \"carbs\": 52, \"fat\": 9}\n```";

        Map<String, Object> meal = service.parseRandomMealResponse(apiResponse(modelText));

        assertThat(meal.get("description")).isEqualTo("Chicken \"Power\" Bowl");
        assertThat((List<?>) meal.get("ingredients")).hasSize(4);
        assertThat((List<?>) meal.get("steps")).hasSize(3);
        assertThat(meal.get("calories")).isEqualTo(520);
        assertThat(meal.get("fat")).isEqualTo(9);
    }

    @Test
    void rejectsSuggestionsThatAreNotUsable() throws Exception {
        assertThat(service.parseRandomMealResponse(apiResponse("Sorry, I can't help with that."))).isNull();
        assertThat(service.parseRandomMealResponse(apiResponse(
                "{\"name\": \"Mystery\", \"ingredients\": [], \"steps\": [], \"calories\": 500, \"protein\": 30, \"carbs\": 50, \"fat\": 15}"))).isNull();
        assertThat(service.parseRandomMealResponse(apiResponse(
                "{\"name\": \"Huge\", \"ingredients\": [\"1 cow\"], \"steps\": [], \"calories\": 99999, \"protein\": 30, \"carbs\": 50, \"fat\": 15}"))).isNull();
    }
}
