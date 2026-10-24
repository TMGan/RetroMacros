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
    @Autowired private com.mealplanner.service.AiQuotaService aiQuotaService;

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
                .andExpect(content().string(containsString("value=\"178\"")))
                .andExpect(content().string(containsString("Build my plan")));
    }

    @Test
    void goalWeightCanBeSetInThePlanForm() throws Exception {
        User client = invitedClient();
        mvc.perform(plan().param("acceptDisclaimer", "true").param("goalWeight", "185").with(signedInAs(client)))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(client.getId()).orElseThrow().getGoalWeightLbs()).isEqualTo(185.0);

        mvc.perform(plan().param("goalWeight", "20").with(signedInAs(client)))
                .andExpect(content().string(containsString("Goal weight must be between 70 and 700 lb.")));
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
    void foodPicksOnThePlanFormBecomeTheClientsPreferences() throws Exception {
        User client = invitedClient();
        mvc.perform(plan().param("acceptDisclaimer", "true")
                        .param("likedFoods", "Ground beef", "Rice", "Eggs", "Not a real option")
                        .param("otherLikes", "cottage cheese").param("dislikes", "fish, olives").param("dietStyle", "flexible")
                        .with(signedInAs(client)))
                .andExpect(status().isOk());

        String prompt = preferencesService.buildPreferencesPrompt(client);
        assertThat(prompt).contains("Ground beef, Rice, Eggs, cottage cheese").doesNotContain("Not a real option");
        assertThat(prompt).contains("Foods to avoid: fish, olives");

        // Next time the form starts with their picks already tapped.
        mvc.perform(get("/form").with(signedInAs(client)))
                .andExpect(content().string(containsString("value=\"Ground beef\" checked")))
                .andExpect(content().string(containsString("value=\"fish, olives\"")));
    }

    @Test
    void foodPreferencesLiveOnTheProfilePage() throws Exception {
        User client = invitedClient();
        mvc.perform(post("/preferences/save").param("preferredFoods", "salmon, rice").param("avoidedFoods", "mushrooms")
                        .param("cookingPreference", "mixed").param("dietaryStyle", "pescatarian")
                        .with(signedInAs(client)).with(csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/profile#preferences"));

        mvc.perform(get("/profile").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("salmon, rice")))
                .andExpect(content().string(containsString("value=\"pescatarian\" selected")));
        mvc.perform(get("/dashboard").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Log food")))
                .andExpect(content().string(not(containsString("salmon, rice"))));
    }

    @Test
    void afterAccessEndsEverythingIsReadOnly() throws Exception {
        User client = invitedClient();
        client.setAccessUntil(java.time.LocalDate.now().minusDays(1));
        userRepository.save(client);

        // Looking is fine.
        mvc.perform(get("/dashboard").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The challenge has ended")))
                .andExpect(content().string(not(containsString("data-bs-target=\"#logFoodModal\""))));
        mvc.perform(get("/progress").with(signedInAs(client))).andExpect(status().isOk());

        // Making or saving anything is not: page forms go back to Today, the page's fetch calls get an error.
        mvc.perform(plan().param("acceptDisclaimer", "true").header("Accept", "text/html").with(signedInAs(client)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/dashboard?ended"));
        assertThat(savedMealPlanRepository.findByUserOrderByCreatedAtDesc(client)).isEmpty();
        mvc.perform(post("/food-log/estimate-macros").param("foodDescription", "two eggs").with(signedInAs(client)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("read-only")));

        // The coach extends them and they're back in.
        client.setAccessUntil(java.time.LocalDate.now().plusDays(30));
        userRepository.save(client);
        mvc.perform(plan().param("acceptDisclaimer", "true").with(signedInAs(client))).andExpect(status().isOk());
        assertThat(savedMealPlanRepository.findByUserOrderByCreatedAtDesc(client)).hasSize(1);
    }

    @Test
    void aFailedPlanDoesNotUseUpOneOfTheWeeklyPlans() throws Exception {
        User client = invitedClient();
        // The offline sample plan contains dairy, so this fails the same way an AI outage would.
        mvc.perform(plan().param("acceptDisclaimer", "true").param("allergies", "Dairy").with(signedInAs(client)))
                .andExpect(content().string(containsString("Dairy")));
        assertThat(aiQuotaService.plansThisWeek(client)).isZero();

        mvc.perform(plan().param("acceptDisclaimer", "true").with(signedInAs(client))).andExpect(status().isOk());
        assertThat(aiQuotaService.plansThisWeek(client)).isEqualTo(1);
    }

    @Test
    void challengePageShowsTheRoutineAndWeeklyPlanCount() throws Exception {
        mvc.perform(get("/challenge").with(signedInAs(invitedClient())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("6-Week Challenge")))
                .andExpect(content().string(containsString("Your routine")))
                .andExpect(content().string(containsString("You have used 0 of 2 this week.")));
    }

    @Test
    void dashboardShowsTheGettingStartedChecklistUntilDone() throws Exception {
        User client = invitedClient();
        mvc.perform(get("/dashboard").with(signedInAs(client)))
                .andExpect(content().string(containsString("Getting started")))
                .andExpect(content().string(containsString("Make your food plan")));
    }

    @Test
    void disclaimerIsPublic() throws Exception {
        mvc.perform(get("/disclaimer"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not medical advice")));
    }
}
