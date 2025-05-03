package org.example.apimywebsite.util;


import jakarta.servlet.http.HttpServletRequest;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthHelper {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;


    public User getUserFromAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        User user = userRepository.findByUserName(username);
        if (user == null || !jwtUtil.isTokenValid(token, username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token or user not found");
        }

        return user;
    }


    public User getUserFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return getUserFromAuthHeader(authHeader);
    }
}
