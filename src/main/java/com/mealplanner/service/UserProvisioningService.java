package com.mealplanner.service;

import com.mealplanner.model.User;
import com.mealplanner.repository.AllowedEmailRepository;
import com.mealplanner.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs on every Google sign-in: enforces the invite list, then creates or
 * updates the matching {@link User} row.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private final UserRepository userRepository;
    private final AllowedEmailRepository allowedEmailRepository;
    private final boolean inviteOnly;
    private final Set<String> adminEmails;

    public UserProvisioningService(UserRepository userRepository,
                                   AllowedEmailRepository allowedEmailRepository,
                                   @Value("${app.invite-only:false}") boolean inviteOnly,
                                   @Value("${app.admin-emails:}") String adminEmails) {
        this.userRepository = userRepository;
        this.allowedEmailRepository = allowedEmailRepository;
        this.inviteOnly = inviteOnly;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (inviteOnly && this.adminEmails.isEmpty()) {
            log.warn("app.invite-only is on but app.admin-emails (ADMIN_EMAILS) is empty: nobody can sign in or invite clients.");
        }
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    public User onLogin(String email, String name, String googleId, String picture) {
        boolean admin = isAdminEmail(email);
        if (inviteOnly && !admin && (email == null || !allowedEmailRepository.existsByEmailIgnoreCase(email.trim()))) {
            log.info("Rejected sign-in for an address that is not on the invite list");
            throw new OAuth2AuthenticationException(new OAuth2Error("not_invited"), "This account has not been invited");
        }

        // Prefer email, but fall back to googleId if email missing
        User user = null;
        if (email != null) {
            user = userRepository.findByEmail(email).orElse(null);
        }
        if (user == null && googleId != null) {
            user = userRepository.findByGoogleId(googleId).orElse(null);
        }

        if (user == null) {
            String displayName = (name != null) ? name : (email != null ? email : "User");
            String gid = (googleId != null) ? googleId : (email != null ? email : "unknown");
            String em = (email != null) ? email : gid + "@google.local";
            user = new User(em, displayName, gid);
        }

        user.setLastLoginAt(LocalDateTime.now());
        if (picture != null && !picture.equals(user.getProfilePictureUrl())) {
            user.setProfilePictureUrl(picture);
        }
        if (googleId != null && !googleId.equals(user.getGoogleId())) {
            user.setGoogleId(googleId);
        }
        // update email/name if they were missing previously
        if (email != null && (user.getEmail() == null || user.getEmail().endsWith("@google.local"))) {
            user.setEmail(email);
        }
        if (name != null && (user.getName() == null || user.getName().isBlank())) {
            user.setName(name);
        }
        if (admin) {
            user.setAdmin(true);
        }
        return userRepository.save(user);
    }
}
