package com.mealplanner;

import com.mealplanner.config.LegacyMeasurementMigration;
import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.MeasurementEntry;
import com.mealplanner.model.User;
import com.mealplanner.repository.BodyCheckInRepository;
import com.mealplanner.repository.MeasurementEntryRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.MeasurementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
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
class MeasurementFeatureTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MeasurementEntryRepository measurementRepository;
    @Autowired private BodyCheckInRepository checkInRepository;
    @Autowired private MeasurementService measurementService;
    @Autowired private LegacyMeasurementMigration migration;

    private User newUser(boolean admin) {
        String id = UUID.randomUUID().toString();
        User user = new User(id + "@example.com", "Client", "gid-" + id);
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

    @Test
    void savesASessionFromTheDiagramAndRemembersTheFigure() throws Exception {
        User client = newUser(false);
        mvc.perform(post("/progress/measurements").param("diagram", "female")
                        .param("m_waist", "31.5").param("m_hips", "40").param("m_armLeft", "11.2").param("m_calfRight", "")
                        .with(signedInAs(client)).with(csrf()))
                .andExpect(redirectedUrl("/progress#measurements"))
                .andExpect(flash().attributeExists("measurementSuccess"));

        List<MeasurementEntry> saved = measurementRepository.findByUserOrderByMeasuredOnAsc(client);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getWaistIn()).isEqualTo(31.5);
        assertThat(saved.get(0).getArmLeftIn()).isEqualTo(11.2);
        assertThat(saved.get(0).getCalfRightIn()).isNull();
        assertThat(userRepository.findById(client.getId()).orElseThrow().getBodyDiagram()).isEqualTo("female");

        // Same day again: adds to the session instead of creating a second one.
        mvc.perform(post("/progress/measurements").param("m_neck", "13").with(signedInAs(client)).with(csrf()));
        saved = measurementRepository.findByUserOrderByMeasuredOnAsc(client);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getNeckIn()).isEqualTo(13.0);
        assertThat(saved.get(0).getWaistIn()).isEqualTo(31.5);
    }

    @Test
    void rejectsBadMeasurements() throws Exception {
        User client = newUser(false);
        mvc.perform(post("/progress/measurements").with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("measurementError", "Enter at least one measurement."));
        mvc.perform(post("/progress/measurements").param("m_waist", "300").with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("measurementError", containsString("Waist should be between")));
        mvc.perform(post("/progress/measurements").param("m_waist", "abc").with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("measurementError", containsString("Waist")));
        mvc.perform(post("/progress/measurements").param("m_waist", "32").param("date", LocalDate.now().plusDays(1).toString())
                        .with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("measurementError", containsString("today or up to")));
        assertThat(measurementRepository.findByUserOrderByMeasuredOnAsc(client)).isEmpty();
    }

    @Test
    void totalInchesAddsUpEverySpotMeasuredTwice() {
        User client = newUser(false);
        MeasurementEntry first = new MeasurementEntry();
        first.setUser(client);
        first.setMeasuredOn(LocalDate.now().minusDays(30));
        first.setWaistIn(36.0);
        first.setHipsIn(40.0);
        first.setArmLeftIn(14.0);
        first.setNeckIn(16.0);   // only measured once, so it is left out of the total
        measurementRepository.save(first);
        MeasurementEntry second = new MeasurementEntry();
        second.setUser(client);
        second.setMeasuredOn(LocalDate.now());
        second.setWaistIn(34.5);
        second.setHipsIn(39.0);
        second.setArmLeftIn(14.5);
        measurementRepository.save(second);

        Map<String, Object> s = measurementService.summary(client);

        assertThat(s.get("totalChange")).isEqualTo(-2.0);
        assertThat(s.get("totalLost")).isEqualTo(2.0);
        assertThat(s.get("waistChange")).isEqualTo(-1.5);
        assertThat(s.get("due")).isEqualTo(false);
        assertThat(measurementService.isDue(newUser(false))).isTrue();
    }

    @Test
    void reminderComesBackAfterFourWeeks() {
        User client = newUser(false);
        MeasurementEntry old = new MeasurementEntry();
        old.setUser(client);
        old.setMeasuredOn(LocalDate.now().minusDays(MeasurementService.REMIND_AFTER_DAYS));
        old.setWaistIn(33.0);
        measurementRepository.save(old);
        assertThat(measurementService.isDue(client)).isTrue();
    }

    @Test
    void measurementsShowForTheCoachButNotOnTheClientsProgressPage() throws Exception {
        User client = newUser(false);
        User coach = newUser(true);
        measurementService.save(client, LocalDate.now().minusDays(29), Map.of(com.mealplanner.model.BodyPart.WAIST, 35.0));
        measurementService.save(client, LocalDate.now(), Map.of(com.mealplanner.model.BodyPart.WAIST, 34.0));

        // Clients get the simple page: no measurements tab, form or totals.
        mvc.perform(get("/progress").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"m_calfLeft\""))))
                .andExpect(content().string(not(containsString("Total inches lost"))));
        mvc.perform(get("/admin/users/" + client.getId()).with(signedInAs(coach)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("measurementData")));
    }

    @Test
    void oldCheckInMeasurementsMoveOverOnce() {
        User client = newUser(false);
        BodyCheckIn legacy = new BodyCheckIn();
        legacy.setUser(client);
        legacy.setCheckInDate(LocalDate.now().minusDays(3));
        legacy.setWeightLbs(180.0);
        legacy.setWaistIn(34.0);
        legacy.setChestIn(40.0);
        legacy.setArmIn(14.0);
        checkInRepository.save(legacy);

        migration.run(null);
        migration.run(null);

        List<MeasurementEntry> moved = measurementRepository.findByUserOrderByMeasuredOnAsc(client);
        assertThat(moved).hasSize(1);
        assertThat(moved.get(0).getWaistIn()).isEqualTo(34.0);
        assertThat(moved.get(0).getChestIn()).isEqualTo(40.0);
        BodyCheckIn cleared = checkInRepository.findByUserOrderByCheckInDateAsc(client).get(0);
        assertThat(cleared.getWaistIn()).isNull();
        assertThat(cleared.getArmIn()).isNull();
        assertThat(cleared.getWeightLbs()).isEqualTo(180.0);
    }

    @Test
    void demoAccountShowsInchesLost() throws Exception {
        mvc.perform(post("/demo-login").with(csrf())).andExpect(redirectedUrl("/dashboard"));
        User demo = userRepository.findByEmail("demo@retromacros.app").orElseThrow();
        assertThat(measurementRepository.findByUserOrderByMeasuredOnAsc(demo)).hasSize(2);
        assertThat(measurementService.summary(demo).get("totalLost")).isEqualTo(4.2);
    }
}
