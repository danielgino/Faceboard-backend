package org.example.apimywebsite.dto;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.example.apimywebsite.configuration.PasswordPolicy;
import org.example.apimywebsite.enums.Gender;


@Data
public class UpdateUserDTO {

    @Pattern(regexp = "^[A-Za-z\u0590-\u05FF]{2,30}$", message = "Name must contain only letters (2-30 characters)")
    private String name;

    @Pattern(regexp = "^[A-Za-z\u0590-\u05FF]{2,30}$", message = "Last name must contain only letters (2-30 characters)")
    private String lastname;

    @Size(max = 200, message = "Biography must be up to 150 characters")
    private String bio;

    @Email(message = "Email must be valid")
    private String email;


    private Gender gender;
@Pattern(
        regexp = PasswordPolicy.REGEX,
        message = PasswordPolicy.MESSAGE
)
    private String newPassword;

    private String currentPassword;

    @Pattern(
            regexp = "^$|^(https?://)?(www\\.)?facebook\\.com/[^\\s]+$",
            message = "Facebook URL must be a valid facebook.com link"
    )
    private String facebookUrl;
    @Pattern(
            regexp = "^$|^(https?://)?(www\\.)?instagram\\.com/[^\\s]+$",
            message = "Instagram URL must be a valid instagram.com link"
    )
    private String instagramUrl;
}