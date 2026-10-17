package com.mealplanner.repository;

import com.mealplanner.model.MeasurementEntry;
import com.mealplanner.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeasurementEntryRepository extends JpaRepository<MeasurementEntry, Long> {
    List<MeasurementEntry> findByUserOrderByMeasuredOnAsc(User user);
    Optional<MeasurementEntry> findByUserAndMeasuredOn(User user, LocalDate measuredOn);
    Optional<MeasurementEntry> findTopByUserOrderByMeasuredOnDesc(User user);
}
