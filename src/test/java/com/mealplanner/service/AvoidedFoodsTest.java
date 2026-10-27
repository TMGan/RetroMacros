package com.mealplanner.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvoidedFoodsTest {

    @Test
    void pullsOutJustTheFoodsHoweverTheyAreWritten() {
        assertThat(UserFoodPreferencesService.parseAvoidedFoods("salmon")).containsExactly("salmon");
        assertThat(UserFoodPreferencesService.parseAvoidedFoods("No salmon")).containsExactly("salmon");
        assertThat(UserFoodPreferencesService.parseAvoidedFoods("I don't like mushrooms and olives."))
                .containsExactly("mushrooms", "olives");
        assertThat(UserFoodPreferencesService.parseAvoidedFoods("salmon, tuna; brussels sprouts"))
                .containsExactly("salmon", "tuna", "brussels sprouts");
        assertThat(UserFoodPreferencesService.parseAvoidedFoods("i really hate liver or onions"))
                .containsExactly("liver", "onions");
    }
}
