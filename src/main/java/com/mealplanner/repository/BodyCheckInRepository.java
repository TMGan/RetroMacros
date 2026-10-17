package com.mealplanner.repository;

import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BodyCheckInRepository extends JpaRepository<BodyCheckIn, Long> {
    List<BodyCheckIn> findByUserOrderByCheckInDateAsc(User user);
    Optional<BodyCheckIn> findByUserAndCheckInDate(User user, LocalDate checkInDate);
    Optional<BodyCheckIn> findTopByUserOrderByCheckInDateDesc(User user);
}
