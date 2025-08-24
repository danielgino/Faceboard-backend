package org.example.apimywebsite.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.apimywebsite.enums.FriendshipStatus;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "friends")
public class Friends {
    @EmbeddedId
    private FriendshipId id;
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User user;


    @ManyToOne
    @JoinColumn(name = "friend_id", referencedColumnName = "id", insertable = false, updatable = false)
    private User friend;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Friends() {}

    public Friends(User user, User friend, FriendshipStatus status, LocalDateTime createdAt) {
        this.user = user;
        this.friend = friend;
        this.status = status;
        this.createdAt = createdAt;
        this.id = new FriendshipId(user.getId(), friend.getId());
    }


}
