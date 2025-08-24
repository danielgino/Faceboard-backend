package org.example.apimywebsite.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.example.apimywebsite.common.Person;
import java.time.OffsetDateTime;

@Setter
@Getter
public class FriendDTO implements Person {
    private int id;
    private String name;
    private String lastname;
    private String username;
    private  String profilePictureUrl;

    private String lastMessageContent;
    private OffsetDateTime lastMessageTime;
    private boolean sentByCurrentUser;

    private int unreadCount = 0;


    public FriendDTO(int id, String name, String lastname, String username, String profilePictureUrl) {
        this.id = id;
        this.name = name;
        this.lastname = lastname;
        this.profilePictureUrl=profilePictureUrl;
        this.username=username;
    }
public FriendDTO(){

}
    @Override
    @JsonProperty("fullName")
    public String getFullName() {
        return name + " " + lastname;
    }

}
