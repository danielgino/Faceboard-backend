package org.example.apimywebsite.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// API-001 fix: email blank/null was already checked (UserService.loginByEmail's own guard), but
// a null password reached PasswordEncoder.matches(null, ...) unvalidated, which throws
// IllegalArgumentException - an unhandled RuntimeException falling through to
// GlobalExceptionHandler's generic 500, not a clean 400 for what is an ordinary missing-field
// client mistake.
@Data
public class LoginRequestDTO {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

}
