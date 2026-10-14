package com.mealplanner.service;

import com.mealplanner.model.User;
import com.mealplanner.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the signed-in {@link User} from the OAuth principal. */
@Service
public class CurrentUserService {

    public static final String VIEW_AS_FLAG = "impersonating";
    public static final String VIEW_AS_USER_ID = "impersonateUserId";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns the signed-in user, or null if there is none. */
    public User find(OAuth2User principal) {
        if (principal == null) return null;
        String email = principal.getAttribute("email");
        String googleId = principal.getAttribute("sub");
        User user = null;
        if (email != null) user = userRepository.findByEmail(email).orElse(null);
        if (user == null && googleId != null) user = userRepository.findByGoogleId(googleId).orElse(null);
        return user;
    }

    /** Returns the signed-in user, or fails the request if there is none. */
    public User require(OAuth2User principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        User user = find(principal);
        if (user == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No account for this sign-in");
        return user;
    }

    /**
     * The user whose data a read-only page should show: the client an admin is
     * viewing as, or otherwise the signed-in user. Never use this for writes.
     */
    public User requireForView(OAuth2User principal, HttpSession session) {
        User actual = require(principal);
        if (actual.isAdmin() && isViewingAs(session)) {
            Object id = session.getAttribute(VIEW_AS_USER_ID);
            if (id instanceof Long) {
                User target = userRepository.findById((Long) id).orElse(null);
                if (target != null) return target;
            }
        }
        return actual;
    }

    public boolean isViewingAs(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(VIEW_AS_FLAG));
    }

    public boolean isDemo(User user) {
        return user != null && DemoAccountService.DEMO_EMAIL.equalsIgnoreCase(user.getEmail());
    }
}
