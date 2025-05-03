package org.example.apimywebsite.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Setter
@Getter
@Embeddable
public class FriendshipId implements Serializable {


    // Getters and Setters
    @Column(name = "user_id")
    private int userId;

    @Column(name = "friend_id")
    private int friendId;


    public FriendshipId(int user, int friendId) {

        this.userId = user;
        this.friendId = friendId;
    }
    public FriendshipId(){

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FriendshipId that = (FriendshipId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(friendId, that.friendId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, friendId);
    }
}
