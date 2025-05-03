package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;

public class LikeDTO {


    private int userId;
    @Setter
    @Getter
    private String fullName;
    @Setter
    @Getter
    private String profilePictureUrl;

      @Getter
      @Setter
      private String username;

    public LikeDTO(Integer userId, String fullName,String userName, String profilePictureUrl) {
        this.userId = userId;
        this.fullName = fullName;
        this.profilePictureUrl = profilePictureUrl;
        this.username =userName;
    }
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

}
