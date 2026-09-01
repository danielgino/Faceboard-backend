package org.example.apimywebsite.util;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthHelper {

    private final UserRepository userRepository;

    public AuthHelper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // H8b: was a plain RuntimeException, always mapped to 400 by the generic handler.
            // Missing/invalid authenticated identity is a 401, not a 400.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String username = authentication.getName();
        return userRepository.findByUserName(username);
    }
}
