package com.mealplanner;

import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.User;
import com.mealplanner.repository.AdminAuditLogRepository;
import com.mealplanner.repository.BodyCheckInRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.ProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"GOOGLE_CLIENT_ID=test", "GOOGLE_CLIENT_SECRET=test", "ai.mock=true", "demo.enabled=true"})
@AutoConfigureMockMvc
class ProgressFeatureTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BodyCheckInRepository checkInRepository;
    @Autowired private AdminAuditLogRepository auditLogRepository;
    @Autowired private ProgressService progressService;

    private User newUser(boolean admin) {
        String id = UUID.randomUUID().toString();
        User user = new User(id + "@example.com", "Client " + id.substring(0, 4), "gid-" + id);
        user.setAdmin(admin);
        return userRepository.save(user);
    }

    private RequestPostProcessor signedInAs(User user) {
        return oauth2Login().attributes(a -> {
            a.put("sub", user.getGoogleId());
            a.put("email", user.getEmail());
            a.put("name", user.getName());
        });
    }

    private MockHttpServletRequestBuilder checkIn(String weight) {
        return post("/progress/check-in").param("weight", weight);
    }

    private void seed(User user, int daysAgo, double weight, Double bodyFat, Double waist) {
        BodyCheckIn e = new BodyCheckIn();
        e.setUser(user);
        e.setCheckInDate(LocalDate.now().minusDays(daysAgo));
        e.setWeightLbs(weight);
        e.setBodyFatPct(bodyFat);
        e.setWaistIn(waist);
        checkInRepository.save(e);
    }

    @Test
    void checkInSavesAndSameDayUpdatesInsteadOfDuplicating() throws Exception {
        User client = newUser(false);
        mvc.perform(checkIn("182.4").param("bodyFat", "21.5").param("energy", "4").param("note", "Felt good")
                        .with(signedInAs(client)).with(csrf()))
                .andExpect(redirectedUrl("/progress#overview"))
                .andExpect(flash().attribute("success", "Check-in saved."));
        mvc.perform(checkIn("181.9").with(signedInAs(client)).with(csrf())).andExpect(redirectedUrl("/progress#overview"));

        List<BodyCheckIn> saved = checkInRepository.findByUserOrderByCheckInDateAsc(client);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getWeightLbs()).isEqualTo(181.9);
    }

    @Test
    void rejectsBadInput() throws Exception {
        User client = newUser(false);
        mvc.perform(checkIn("12").with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Weight")));
        mvc.perform(checkIn("180").param("energy", "9").with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Scores")));
        mvc.perform(checkIn("180").param("date", LocalDate.now().plusDays(3).toString()).with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("today or up to")));
        mvc.perform(checkIn("180").param("date", LocalDate.now().minusDays(60).toString()).with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("today or up to")));
        assertThat(checkInRepository.findByUserOrderByCheckInDateAsc(client)).isEmpty();
    }

    @Test
    void progressPageShowsOnlyYourOwnData() throws Exception {
        User alice = newUser(false);
        User bob = newUser(false);
        seed(alice, 14, 190.0, 24.0, 38.0);
        seed(alice, 0, 186.2, 23.0, 37.1);

        mvc.perform(get("/progress").with(signedInAs(alice)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("186.2")))
                .andExpect(content().string(containsString("Lean vs fat mass")))
                .andExpect(content().string(containsString("chart.umd.min.js")));
        mvc.perform(get("/progress").with(signedInAs(bob)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your charts show up here after your first check-in")))
                .andExpect(content().string(not(containsString("186.2"))));
    }

    @Test
    void summaryNumbersAreRight() {
        User client = newUser(false);
        client.setGoalWeightLbs(175.0);
        userRepository.save(client);
        seed(client, 21, 190.0, 25.0, 38.0);
        seed(client, 14, 188.0, null, null);
        seed(client, 7, 186.0, null, null);
        seed(client, 0, 184.0, 23.0, 36.5);

        Map<String, Object> s = progressService.summary(client);

        assertThat(s.get("startWeight")).isEqualTo(190.0);
        assertThat(s.get("currentWeight")).isEqualTo(184.0);
        assertThat(s.get("change")).isEqualTo(-6.0);
        assertThat(s.get("weeklyRate")).isEqualTo(-2.0);
        assertThat(s.get("goalType")).isEqualTo("lose");
        assertThat(s.get("toGoalAbs")).isEqualTo(9.0);
        assertThat(s.get("projectedDate")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> composition = (Map<String, Object>) s.get("composition");
        assertThat(composition.get("startFat")).isEqualTo(47.5);
        assertThat(composition.get("currentFat")).isEqualTo(42.3);
        assertThat(composition.get("leanChange")).isEqualTo(-0.8);
        assertThat(s.get("missedCheckIn")).isEqualTo(false);
    }

    @Test
    void goalCanBeSetByTheClientAndOverriddenByTheCoach() throws Exception {
        User client = newUser(false);
        User coach = newUser(true);
        mvc.perform(post("/progress/goal").param("goalWeight", "175").with(signedInAs(client)).with(csrf()))
                .andExpect(redirectedUrl("/progress#overview"));
        assertThat(userRepository.findById(client.getId()).orElseThrow().getGoalWeightLbs()).isEqualTo(175.0);

        long audits = auditLogRepository.count();
        mvc.perform(post("/admin/users/" + client.getId() + "/goal").param("goalWeight", "178.5").with(signedInAs(coach)).with(csrf()))
                .andExpect(redirectedUrl("/admin/users/" + client.getId() + "?goalSaved=true"));
        assertThat(userRepository.findById(client.getId()).orElseThrow().getGoalWeightLbs()).isEqualTo(178.5);
        assertThat(auditLogRepository.count()).isEqualTo(audits + 1);

        // A client cannot use the coach's endpoint.
        mvc.perform(post("/admin/users/" + client.getId() + "/goal").param("goalWeight", "100").with(signedInAs(client)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coachSeesProgressAndCannotCheckInWhileViewingAsClient() throws Exception {
        User client = newUser(false);
        User coach = newUser(true);
        seed(client, 7, 200.0, 30.0, 40.0);
        seed(client, 0, 197.5, 29.0, 39.0);

        mvc.perform(get("/admin/users/" + client.getId()).with(signedInAs(coach)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Recent check-ins")))
                .andExpect(content().string(containsString("197.5")));
        mvc.perform(get("/admin/users").with(signedInAs(coach))).andExpect(status().isOk());
        mvc.perform(get("/admin").with(signedInAs(coach)))
                .andExpect(content().string(containsString("Missed check-in")));

        jakarta.servlet.http.Cookie viewAs = mvc.perform(post("/admin/users/" + client.getId() + "/impersonate").with(signedInAs(coach)).with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        mvc.perform(get("/progress").with(signedInAs(coach))
                        .cookie(viewAs))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("197.5")));
        mvc.perform(checkIn("150").with(signedInAs(coach)).with(csrf())
                        .cookie(viewAs))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoAccountComesWithProgressData() throws Exception {
        mvc.perform(post("/demo-login").with(csrf())).andExpect(redirectedUrl("/dashboard"));
        User demo = userRepository.findByEmail("demo@retromacros.app").orElseThrow();
        assertThat(checkInRepository.findByUserOrderByCheckInDateAsc(demo)).hasSizeGreaterThan(10);
        mvc.perform(get("/progress").with(signedInAs(demo)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your habits")));
    }

    @Test
    void deletingAClientRemovesTheirCheckIns() throws Exception {
        User client = newUser(false);
        User coach = newUser(true);
        seed(client, 0, 180.0, null, null);
        mvc.perform(post("/admin/users/" + client.getId() + "/delete").with(signedInAs(coach)).with(csrf()))
                .andExpect(redirectedUrl("/admin/users?deleted=true"));
        assertThat(checkInRepository.findAll().stream().noneMatch(c -> c.getUser().getId().equals(client.getId()))).isTrue();
    }

    @Test
    void progressPageHasASectionMenu() throws Exception {
        String html = mvc.perform(get("/progress").with(signedInAs(newUser(false))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String id : java.util.List.of("checkin", "overview", "charts", "measurements")) {
            assertThat(html).contains("data-section=\"" + id + "\"");
            assertThat(html).contains("<section class=\"progress-section\" id=\"" + id + "\"");
        }
        // A client who hasn't checked in today starts on the check-in.
        assertThat(html).contains("data-default=\"checkin\"");
    }
}
