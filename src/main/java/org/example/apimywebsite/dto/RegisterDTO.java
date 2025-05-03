package org.example.apimywebsite.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import org.example.apimywebsite.api.model.Gender;

import java.time.LocalDate;

public class RegisterDTO {

    @NotBlank(message = "Username is required")
    @Pattern(
            regexp = "^[a-zA-Z0-9_.]{3,15}$",
            message = "Username must be 3-15 characters long and contain only letters, numbers, and underscores (_) and dots(.)"
    )
    private String username;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}:\";'<>?,./]).{8,}$",
            message = "Password must include upper/lowercase letters, a number, and a special character"
    )
    private String password;

    @NotBlank(message = "First name is required")
    @Pattern(regexp = "^[A-Za-z\u0590-\u05FF]+$", message = "Name must contain only letters")
    @Size(min = 2, message = "Name must be at least 2 characters")
    private String name;

    @NotBlank(message = "Last name is required")
    @Pattern(regexp = "^[A-Za-z\u0590-\u05FF]+$", message = "Last name must contain only letters")
    @Size(min = 2, message = "Lastname must be at least 2 characters")
    private String lastname;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    private String email;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Past(message = "Birth date must be in the past")
    @NotNull(message = "Birth date is required")
   private LocalDate birthDate;


    @NotNull(message = "Gender is required")
    private Gender gender;
    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

}
