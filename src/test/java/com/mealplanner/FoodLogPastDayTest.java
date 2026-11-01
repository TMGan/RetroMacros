package com.mealplanner;

import com.mealplanner.model.FoodLog;
import com.mealplanner.model.User;
import com.mealplanner.repository.FoodLogRepository;
import com.mealplanner.repository.UserRepository;
import com.mealplanner.service.FoodLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"GOOGLE_CLIENT_ID=test", "GOOGLE_CLIENT_SECRET=test", "ai.mock=true"})
@AutoConfigureMockMvc
class FoodLogPastDayTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private FoodLogRepository foodLogRepository;
    @Autowired private FoodLogService foodLogService;

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepository.save(new User(id + "@example.com", "Client", "gid-" + id));
    }

    private RequestPostProcessor signedInAs(User user) {
        return oauth2Login().attributes(a -> {
            a.put("sub", user.getGoogleId());
            a.put("email", user.getEmail());
            a.put("name", user.getName());
        });
    }

    private MockHttpServletRequestBuilder add(LocalDate day) {
        MockHttpServletRequestBuilder request = post("/food-log/add")
                .param("mealName", "Dinner").param("foodDescription", "Chicken and rice")
                .param("calories", "500").param("protein", "40").param("carbs", "50").param("fat", "12");
        return day == null ? request : request.param("logDate", day.toString());
    }

    @Test
    void foodAddedToAPastDayCountsTowardThatDay() throws Exception {
        User client = newUser();
        LocalDate today = foodLogService.today(client);
        LocalDate yesterday = today.minusDays(1);

        mvc.perform(add(yesterday).with(signedInAs(client)).with(csrf()))
                .andExpect(redirectedUrl("/food-log/history"))
                .andExpect(flash().attributeExists("success"));

        assertThat(foodLogService.calculateDailyTotals(client, yesterday).get("calories")).isEqualTo(500);
        assertThat(foodLogService.calculateDailyTotals(client, today).get("calories")).isZero();
        FoodLog saved = foodLogService.getFoodLogsByDate(client, yesterday).get(0);
        assertThat(saved.getTimeLogged().toLocalDate()).isEqualTo(yesterday);
    }

    @Test
    void noDateStillMeansTodayAndGoesBackToTheDashboard() throws Exception {
        User client = newUser();
        mvc.perform(add(null).with(signedInAs(client)).with(csrf())).andExpect(redirectedUrl("/dashboard"));
        assertThat(foodLogService.calculateDailyTotals(client, foodLogService.today(client)).get("calories")).isEqualTo(500);
    }

    @Test
    void onlyTheDaysOnTheHistoryPageAreAllowed() throws Exception {
        User client = newUser();
        LocalDate today = foodLogService.today(client);

        mvc.perform(add(today.plusDays(1)).with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("past 6 days")));
        mvc.perform(add(today.minusDays(7)).with(signedInAs(client)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("past 6 days")));
        assertThat(foodLogRepository.findByUserAndLogDateBetweenOrderByLogDateDescTimeLoggedAsc(client, today.minusDays(30), today.plusDays(2))).isEmpty();

        mvc.perform(add(today.minusDays(6)).with(signedInAs(client)).with(csrf())).andExpect(flash().attributeExists("success"));
    }

    @Test
    void theHistoryPageOffersAddFoodEvenWithNothingLogged() throws Exception {
        User client = newUser();
        LocalDate yesterday = foodLogService.today(client).minusDays(1);
        mvc.perform(get("/food-log/history").with(signedInAs(client)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-date=\"" + yesterday + "\"")))
                .andExpect(content().string(containsString("id=\"addFoodModal\"")));
    }

    @Test
    void aMistakeCanBeRemovedFromTheHistoryPageButOnlyYourOwn() throws Exception {
        User client = newUser();
        User stranger = newUser();
        LocalDate yesterday = foodLogService.today(client).minusDays(1);
        mvc.perform(add(yesterday).with(signedInAs(client)).with(csrf()));
        List<FoodLog> logs = foodLogService.getFoodLogsByDate(client, yesterday);

        mvc.perform(post("/food-log/delete/" + logs.get(0).getId()).param("from", "history").with(signedInAs(stranger)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/food-log/delete/" + logs.get(0).getId()).param("from", "history").with(signedInAs(client)).with(csrf()))
                .andExpect(redirectedUrl("/food-log/history"));
        assertThat(foodLogService.getFoodLogsByDate(client, yesterday)).isEmpty();
    }
}
