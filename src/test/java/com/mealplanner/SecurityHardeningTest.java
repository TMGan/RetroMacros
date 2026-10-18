package com.mealplanner;

import com.mealplanner.model.AllowedEmail;
import com.mealplanner.model.FoodLog;
import com.mealplanner.model.User;
import com.mealplanner.repository.AdminAuditLogRepository;
import com.mealplanner.repository.AllowedEmailRepository;
import com.mealplanner.repository.FoodLogRepository;
import com.mealplanner.repository.SavedMealPlanRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.AdminService;
import com.mealplanner.service.UserProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test",
        "GOOGLE_CLIENT_SECRET=test",
        "ai.mock=true",
        "demo.enabled=true",
        "ai.limit.plans-per-day=2"
})
@AutoConfigureMockMvc
class SecurityHardeningTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private FoodLogRepository foodLogRepository;
    @Autowired private SavedMealPlanRepository savedMealPlanRepository;
    @Autowired private AllowedEmailRepository allowedEmailRepository;
    @Autowired private AdminAuditLogRepository auditLogRepository;
    @Autowired private AdminService adminService;

    private User newUser(boolean admin) {
        String id = UUID.randomUUID().toString();
        User user = new User(id + "@example.com", "Test " + id.substring(0, 6), "gid-" + id);
        user.setAdmin(admin);
        return userRepository.save(user);
    }

    private RequestPostProcessor signedInAs(User user) {
        return oauth2Login().attributes(attrs -> {
            attrs.put("sub", user.getGoogleId());
            attrs.put("email", user.getEmail());
            attrs.put("name", user.getName());
        });
    }

    private FoodLog newLog(User owner) {
        FoodLog log = new FoodLog();
        log.setUser(owner);
        log.setMealName("Lunch");
        log.setFoodDescription("chicken and rice");
        log.setCalories(500);
        log.setProtein(40);
        log.setCarbs(50);
        log.setFat(10);
        return foodLogRepository.save(log);
    }

    private MockHttpServletRequestBuilder generatePlan() {
        return post("/generate")
                .param("weight", "180").param("heightFeet", "5").param("heightInches", "10")
                .param("age", "30").param("sex", "male")
                .param("activityLevel", "Moderately Active").param("fitnessGoal", "Maintain Weight")
                .param("acceptDisclaimer", "true");
    }

    @Test
    void signedOutVisitorsAreSentToLogin() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void foodLogCanOnlyBeDeletedByItsOwner() throws Exception {
        User alice = newUser(false);
        User bob = newUser(false);
        FoodLog aliceLog = newLog(alice);

        mvc.perform(post("/food-log/delete/" + aliceLog.getId()).with(signedInAs(bob)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(foodLogRepository.existsById(aliceLog.getId())).isTrue();

        mvc.perform(post("/food-log/delete/" + aliceLog.getId()).with(signedInAs(alice)).with(csrf()))
                .andExpect(redirectedUrl("/dashboard"));
        assertThat(foodLogRepository.existsById(aliceLog.getId())).isFalse();
    }

    @Test
    void aiEndpointsRejectRequestsWithoutCsrfToken() throws Exception {
        User alice = newUser(false);

        mvc.perform(post("/food-log/estimate-macros").param("foodDescription", "two eggs").with(signedInAs(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/swap/record").param("swapType", "ingredient").param("originalFood", "rice")
                        .param("replacementFood", "quinoa").param("mealContext", "Lunch").with(signedInAs(alice)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/food-log/estimate-macros").param("foodDescription", "two eggs")
                        .with(signedInAs(alice)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void foodLogRejectsOutOfRangeMacros() throws Exception {
        User alice = newUser(false);

        mvc.perform(post("/food-log/add").param("mealName", "Lunch").param("foodDescription", "rice")
                        .param("calories", "-500").param("protein", "0").param("carbs", "0").param("fat", "0")
                        .with(signedInAs(alice)).with(csrf()))
                .andExpect(redirectedUrl("/dashboard"));
        assertThat(foodLogRepository.countByUser(alice)).isZero();
    }

    @Test
    void adminAreaIsClosedToNonAdmins() throws Exception {
        User client = newUser(false);
        User coach = newUser(true);

        mvc.perform(get("/admin").with(signedInAs(client))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/invites").with(signedInAs(client))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/export/users").with(signedInAs(client))).andExpect(status().isForbidden());

        mvc.perform(get("/admin").with(signedInAs(coach))).andExpect(status().isOk());
        mvc.perform(get("/admin/invites").with(signedInAs(coach))).andExpect(status().isOk());
        mvc.perform(get("/admin/users/" + client.getId()).with(signedInAs(coach)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI cost this month")));
    }

    @Test
    void everyAdminPageRendersWithData() throws Exception {
        User coach = newUser(true);
        User client = newUser(false);
        newLog(client);
        mvc.perform(generatePlan().with(signedInAs(client)).with(csrf())).andExpect(status().isOk());
        allowedEmailRepository.save(new AllowedEmail(UUID.randomUUID() + "@example.com", "test invite"));
        long planId = savedMealPlanRepository.findByUserOrderByCreatedAtDesc(client).get(0).getId();

        for (String page : new String[] {
                "/admin", "/admin/users", "/admin/users?search=Test&sortBy=name", "/admin/users/" + client.getId(),
                "/admin/invites", "/admin/meal-plans", "/admin/meal-plans?userId=" + client.getId(),
                "/admin/meal-plans/" + planId, "/admin/food-logs", "/admin/food-logs?userId=" + client.getId(),
                "/admin/ai-accuracy"}) {
            mvc.perform(get(page).with(signedInAs(coach)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("theme.css")));
        }
    }

    @Test
    void dailyPlanLimitIsEnforced() throws Exception {
        User alice = newUser(false);

        mvc.perform(generatePlan().with(signedInAs(alice)).with(csrf())).andExpect(status().isOk());
        mvc.perform(generatePlan().with(signedInAs(alice)).with(csrf())).andExpect(status().isOk());
        mvc.perform(generatePlan().with(signedInAs(alice)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("reached today")));

        assertThat(savedMealPlanRepository.countByUser(alice)).isEqualTo(2);
    }

    @Test
    void inviteOnlyRejectsUninvitedAccounts() {
        UserProvisioningService inviteOnly =
                new UserProvisioningService(userRepository, allowedEmailRepository, true, "Coach@Example.com");
        String stranger = UUID.randomUUID() + "@example.com";

        assertThatThrownBy(() -> inviteOnly.onLogin(stranger, "Stranger", "gid-" + stranger, null))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(userRepository.existsByEmail(stranger)).isFalse();

        allowedEmailRepository.save(new AllowedEmail(stranger, "invited"));
        User invited = inviteOnly.onLogin(stranger, "Stranger", "gid-" + stranger, null);
        assertThat(invited.isAdmin()).isFalse();

        User coach = inviteOnly.onLogin("coach@example.com", "Coach", "gid-coach", null);
        assertThat(coach.isAdmin()).isTrue();
    }

    @Test
    void viewingAClientIsReadOnlyAndLogged() throws Exception {
        User client = newUser(false);
        User coach = newUser(true);
        newLog(client);
        long auditBefore = auditLogRepository.count();

        // Sessions live in the database, so carry the session cookie like a browser would.
        jakarta.servlet.http.Cookie viewAs = mvc.perform(post("/admin/users/" + client.getId() + "/impersonate").with(signedInAs(coach)).with(csrf()))
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(viewAs).isNotNull();
        assertThat(auditLogRepository.count()).isEqualTo(auditBefore + 1);

        mvc.perform(get("/dashboard").with(signedInAs(coach))
                        .cookie(viewAs))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("read-only")))
                .andExpect(content().string(containsString("chicken and rice")));

        mvc.perform(post("/food-log/add").param("mealName", "Lunch").param("foodDescription", "rice")
                        .param("calories", "200").param("protein", "4").param("carbs", "45").param("fat", "0")
                        .with(signedInAs(coach)).with(csrf())
                        .cookie(viewAs))
                .andExpect(status().isForbidden());

        // A non-admin cannot use the session flags to see someone else's data.
        User other = newUser(false);
        mvc.perform(get("/dashboard").with(signedInAs(other))
                        .cookie(viewAs))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("chicken and rice"))));
    }

    @Test
    void deletingAUserRemovesTheirData() {
        User client = newUser(false);
        FoodLog log = newLog(client);

        adminService.deleteUser(client.getId());

        assertThat(userRepository.existsById(client.getId())).isFalse();
        assertThat(foodLogRepository.existsById(log.getId())).isFalse();
    }

    @Test
    void demoLoginStillWorks() throws Exception {
        mvc.perform(post("/demo-login").with(csrf())).andExpect(redirectedUrl("/dashboard"));
    }
}
