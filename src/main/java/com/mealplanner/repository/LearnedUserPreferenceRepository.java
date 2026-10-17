package com.mealplanner.repository;

import com.mealplanner.model.LearnedUserPreference;
import com.mealplanner.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LearnedUserPreferenceRepository extends JpaRepository<LearnedUserPreference, Long> {
    List<LearnedUserPreference> findByUserOrderByConfidenceScoreDesc(User user);
    Optional<LearnedUserPreference> findByUserAndPreferenceTypeAndFoodItemAndComparedToFood(
            User user, String preferenceType, String foodItem, String comparedToFood);
    List<LearnedUserPreference> findByUserAndConfidenceScoreGreaterThanEqual(User user, BigDecimal threshold);

    // Strongest first, capped, so a client with many recorded swaps cannot bloat every prompt.
    List<LearnedUserPreference> findTop15ByUserAndConfidenceScoreGreaterThanEqualOrderByConfidenceScoreDescTimesObservedDesc(User user, BigDecimal threshold);
}

