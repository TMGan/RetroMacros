package com.mealplanner.config;

import com.mealplanner.model.BodyCheckIn;
import com.mealplanner.model.MeasurementEntry;
import com.mealplanner.repository.BodyCheckInRepository;
import com.mealplanner.repository.MeasurementEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time move of measurements from the daily check-in (where they first lived)
 * into the monthly measurement record. Waist, hips and chest carry over; the old
 * single arm and thigh values can't be split into left and right, so they are
 * cleared. Safe to run on every startup: once moved, nothing is left to move.
 */
@Component
public class LegacyMeasurementMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyMeasurementMigration.class);

    private final BodyCheckInRepository checkInRepository;
    private final MeasurementEntryRepository measurementRepository;

    public LegacyMeasurementMigration(BodyCheckInRepository checkInRepository, MeasurementEntryRepository measurementRepository) {
        this.checkInRepository = checkInRepository;
        this.measurementRepository = measurementRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int moved = 0;
        for (BodyCheckIn c : checkInRepository.findAll()) {
            if (c.getWaistIn() == null && c.getHipsIn() == null && c.getChestIn() == null
                    && c.getArmIn() == null && c.getThighIn() == null) continue;
            if (c.getWaistIn() != null || c.getHipsIn() != null || c.getChestIn() != null) {
                MeasurementEntry m = measurementRepository.findByUserAndMeasuredOn(c.getUser(), c.getCheckInDate())
                        .orElseGet(MeasurementEntry::new);
                m.setUser(c.getUser());
                m.setMeasuredOn(c.getCheckInDate());
                if (m.getWaistIn() == null) m.setWaistIn(c.getWaistIn());
                if (m.getHipsIn() == null) m.setHipsIn(c.getHipsIn());
                if (m.getChestIn() == null) m.setChestIn(c.getChestIn());
                measurementRepository.save(m);
                moved++;
            }
            c.setWaistIn(null);
            c.setHipsIn(null);
            c.setChestIn(null);
            c.setArmIn(null);
            c.setThighIn(null);
            checkInRepository.save(c);
        }
        if (moved > 0) log.info("Moved measurements from {} check-ins into the measurement record", moved);
    }
}
