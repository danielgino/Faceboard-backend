package org.example.apimywebsite.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

import org.example.apimywebsite.enums.Gender;
import org.example.apimywebsite.common.Person;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "username",
        "email",
        "name",
        "lastname",
        "birthDate",
        "gender",
        "profilePictureUrl",
        "bio",
        "facebookUrl",
        "instagramUrl",
        "fullName",
        "friendsList"
})
public class UserDTO implements Person {

    private int id;
    private String username;
    private String email;
    private String name;
    private String lastname;
    private LocalDate birthDate;
    private Gender gender;
    private String profilePictureUrl;
    private String bio;
    private String facebookUrl;
    private String instagramUrl;
    private List<FriendDTO> friendsList;

    @Override
    @JsonProperty("fullName")
    public String getFullName() {
        return name + " " + lastname;
    }
}

