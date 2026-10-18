package com.mealplanner.repository;

import com.mealplanner.model.AiUsage;
import com.mealplanner.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
    Optional<AiUsage> findByUserAndKindAndUsageDate(User user, String kind, LocalDate usageDate);
    List<AiUsage> findByUser(User user);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(u.requestCount), 0) FROM AiUsage u "
            + "WHERE u.user = :user AND u.kind = :kind AND u.usageDate >= :since")
    long sumRequestsSince(@org.springframework.data.repository.query.Param("user") User user,
                          @org.springframework.data.repository.query.Param("kind") String kind,
                          @org.springframework.data.repository.query.Param("since") LocalDate since);
}
