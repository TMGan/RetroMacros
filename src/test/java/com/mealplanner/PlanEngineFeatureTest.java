package com.mealplanner;

import com.mealplanner.model.AllowedEmail;
import com.mealplanner.model.SavedMealPlan;
import com.mealplanner.model.User;
import com.mealplanner.repository.AllowedEmailRepository;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test",
        "GOOGLE_CLIENT_SECRET=test",
        "ai.mock=true",
        "app.invite-only=true",
        "app.admin-emails=coach@example.com"
})
@AutoConfigureMockMvc
class PlanEngineFeatureTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AllowedEmailRepository allowedEmailRepository;
    @Autowired private SavedMealPlanRepository savedMealPlanRepository;
    @Autowired private com.mealplanner.service.UserFoodPreferencesService preferencesService;

    private User invitedClient() {
        String email = UUID.randomUUID() + "@example.com";
        allowedEmailRepository.save(new AllowedEmail(email, "test"));
        return userRepository.save(new User(email, "Client", "gid-" + email));
    }

    private RequestPostProcessor signedInAs(User user) {
        return oauth2Login().attributes(attrs -> {
            attrs.put("sub", user.getGoogleId());
            attrs.put("email", user.getEmail());
            attrs.put("name", user.getName());
        });
    }

    private MockHttpServletRequestBuilder plan() {
        return post("/generate")
                .param("weight", "200").param("heightFeet", "5").param("heightInches", "10")
                .param("age", "40").param("sex", "Male")
                .param("activityLevel", "Moderately Active").param("fitnessGoal", "Lose Weight")
                .with(csrf());
    }

    @Test
    void formRendersEverySectionForANewClient() throws Exception {
        mvc.perform(get("/form").with(signedInAs(invitedClient())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("How do you like to eat?")))
                .andExpect(content().string(containsString("Kidney disease")))
                .andExpect(content().string(containsString("name=\"acceptDisclaimer\"")))
                .andExpect(content().string(containsString("Build my plan")));
    }

    @Test
    void formRendersFullyFromAClientsHistory() throws Exception {
        User client = invitedClient();
        mvc.perform(plan().param("acceptDisclaimer", "true").with(signedInAs(client))).andExpect(status().isOk());
        User saved = userRepository.findById(client.getId()).orElseThrow();
        saved.setGoalWeightLbs(178.0);
        userRepository.save(saved);

        mvc.perform(get("/form").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"200\"")))
                .andExpect(content().string(not(containsString("name=\"acceptDisclaimer\""))))
                .andExpect(content().string(containsString("<strong>178 lb</strong>")))
                .andExpect(content().string(containsString("Build my plan")));
    }

    @Test
    void planNeedsTheDisclaimerOnce() throws Exception {
        User client = invitedClient();
        mvc.perform(plan().with(signedInAs(client)))
                .andExpect(content().string(containsString("accept the disclaimer")));
        assertThat(savedMealPlanRepository.findByUserOrderByCreatedAtDesc(client)).isEmpty();

        mvc.perform(plan().param("acceptDisclaimer", "true").with(signedInAs(client))).andExpect(status().isOk());
        assertThat(userRepository.findById(client.getId()).orElseThrow().getDisclaimerAcceptedAt()).isNotNull();
    }

    @Test
    void simpleRampedPlanShowsTheWeekAndCreatine() throws Exception {
        User client = invitedClient();
        mvc.perform(plan().param("acceptDisclaimer", "true").param("effortLevel", "simple").param("ramp", "true")
                        .with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Easing in · week 1 of 4")))
                .andExpect(content().string(containsString("Daily supplement: creatine monohydrate")))
                .andExpect(content().string(containsString("Fiber")));

        SavedMealPlan saved = savedMealPlanRepository.findByUserOrderByCreatedAtDesc(client).get(0);
        assertThat(saved.getRampWeek()).isEqualTo(1);
        assertThat(saved.getEffortLevel()).isEqualTo("simple");
        assertThat(saved.getAccuracyScore()).isNotNull();
        assertThat(userRepository.findById(client.getId()).orElseThrow().getRampStartDate()).isNotNull();

        mvc.perform(get("/dashboard").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("easing in week 1 of 4")));
    }

    @Test
    void healthFlagHoldsAtMaintenanceAndShowsForTheCoach() throws Exception {
        User client = invitedClient();
        mvc.perform(plan().param("acceptDisclaimer", "true").param("healthConditions", "kidney")
                        .param("healthNotes", "stage 2, seeing a nephrologist").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("set to maintenance calories")))
                .andExpect(content().string(containsString("ask your doctor before starting it")));

        SavedMealPlan saved = savedMealPlanRepository.findByUserOrderByCreatedAtDesc(client).get(0);
        assertThat(saved.getHealthHold()).isTrue();
        assertThat(saved.getHealthConditions()).isEqualTo("kidney");

        User coach = userRepository.save(new User("coach@example.com", "Coach", "gid-coach-" + UUID.randomUUID()));
        coach.setAdmin(true);
        userRepository.save(coach);
        mvc.perform(get("/admin/users/" + client.getId()).with(signedInAs(coach)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kidney disease")))
                .andExpect(content().string(containsString("stage 2, seeing a nephrologist")));

        mvc.perform(post("/admin/users/" + client.getId() + "/health-clearance").param("cleared", "true")
                .with(signedInAs(coach)).with(csrf()));
        assertThat(userRepository.findById(client.getId()).orElseThrow().isHealthCleared()).isTrue();
    }

    @Test
    void customSplitMustAddUpTo100() throws Exception {
        mvc.perform(plan().param("acceptDisclaimer", "true").param("macroStyle", "custom")
                        .param("proteinPercent", "50").param("carbsPercent", "40").param("fatPercent", "30")
                        .with(signedInAs(invitedClient())))
                .andExpect(content().string(containsString("must add up to 100%")));
    }

    @Test
    void ingredientSwapsSkipAllergiesAndAvoidedFoods() throws Exception {
        User client = invitedClient();
        SavedMealPlan plan = new SavedMealPlan();
        plan.setUser(client);
        plan.setAllergies("Fish, Tree Nuts");
        savedMealPlanRepository.save(plan);
        preferencesService.savePreferences(client, "", "avocado", "mostly-home-cooked", "flexible");

        String proteins = mvc.perform(post("/swap/ingredient-options").param("originalFood", "chicken breast")
                        .param("calories", "0").param("protein", "0").param("carbs", "0").param("fat", "0")
                        .with(signedInAs(client)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(proteins).contains("Ground turkey").doesNotContain("Salmon").doesNotContain("Tilapia");

        String fats = mvc.perform(post("/swap/ingredient-options").param("originalFood", "olive oil")
                        .param("calories", "0").param("protein", "0").param("carbs", "0").param("fat", "0")
                        .with(signedInAs(client)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(fats).contains("Olive oil").contains("Peanut butter")
                .doesNotContain("Almonds").doesNotContain("Walnuts").doesNotContain("Avocado");
    }

    @Test
    void disclaimerIsPublic() throws Exception {
        mvc.perform(get("/disclaimer"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not medical advice")));
    }
}
