package com.mealplanner.repository;

import com.mealplanner.model.AiCallLog;
import com.mealplanner.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    @Query("SELECT COALESCE(SUM(l.costMicros), 0) FROM AiCallLog l")
    long sumCostMicros();

    @Query("SELECT COALESCE(SUM(l.costMicros), 0) FROM AiCallLog l WHERE l.createdAt >= :since")
    long sumCostMicrosSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(l.costMicros), 0) FROM AiCallLog l WHERE l.user = :user")
    long sumCostMicrosForUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(l.costMicros), 0) FROM AiCallLog l WHERE l.user = :user AND l.createdAt >= :since")
    long sumCostMicrosForUserSince(@Param("user") User user, @Param("since") LocalDateTime since);

    long countByUser(User user);

    long countBySuccess(boolean success);

    /** Keeps the cost history but cuts the link to a user who is being deleted. */
    @Modifying
    @Query("UPDATE AiCallLog l SET l.user = null WHERE l.user = :user")
    int detachUser(@Param("user") User user);
}
