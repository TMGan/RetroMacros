package com.mealplanner.repository;

import com.mealplanner.model.AllowedEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllowedEmailRepository extends JpaRepository<AllowedEmail, Long> {
    boolean existsByEmailIgnoreCase(String email);
    List<AllowedEmail> findAllByOrderByInvitedAtDesc();
}
