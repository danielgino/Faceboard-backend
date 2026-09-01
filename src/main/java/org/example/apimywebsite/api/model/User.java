package org.example.apimywebsite.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.apimywebsite.enums.Gender;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true)
    private String userName;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "last_name", nullable = false)
    private String lastname;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(length = 200)
    private String bio;

    @Column(name = "instagram_url", length = 255)
    private String instagramUrl;

    @Column(name = "facebook_url", length = 255)
    private String facebookUrl;

    @Column(name = "profile_picture_url", length = 255)
    private String profilePictureUrl;

    // M-DUP2: the redundant, status-unaware @ManyToMany friends mapping (previously here,
    // @JoinTable(name = "friends", user_id/friend_id) - the same physical table/columns as
    // Friends/FriendshipRepository) was removed. It had zero production consumers and was
    // semantically unsafe: it ignored FriendshipStatus entirely, so a PENDING (not yet accepted)
    // request could already appear as an established friendship. Friends/FriendshipId/
    // FriendshipStatus/FriendshipRepository/FriendshipService remain the sole friendship source
    // of truth; nothing in this entity replaces it.

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<Post> posts = new ArrayList<>();

    public String getFullName() {
        return this.name + " " + this.lastname;
    }

    // H5: JPA-safe identity. Equality is based on the persisted id alone (never on mutable
    // profile fields or the posts relationship), and two transient (id == 0, not yet
    // persisted) instances are only ever equal to themselves - never structurally to each other.
    // effectiveClass() is a private static helper (not a virtual instance method) so calling it
    // on a Hibernate proxy can never itself trigger initialization of the proxy target.
    private static Class<?> effectiveClass(Object o) {
        return (o instanceof HibernateProxy hp)
                ? hp.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        if (effectiveClass(this) != effectiveClass(other)) return false;
        // getId() (not the raw `id` field) is required on both sides: on an uninitialized
        // Hibernate proxy the identifier lives in the LazyInitializer and is served through the
        // proxy's identifier-getter override, while the proxy's own inherited `id` field is
        // never populated and stays at its Java default (0).
        return getId() != 0 && getId() == other.getId();
    }

    // Must be derived the same way as equals()'s identity check: a proxy and its real entity
    // representation are equal, so they must share this hash. A constant per effective class
    // also keeps the bucket stable when the database-generated id is assigned post-construction.
    @Override
    public int hashCode() {
        return effectiveClass(this).hashCode();
    }

    // Deliberately excludes password and the posts relationship: no secrets and no lazy
    // relationship traversal.
    @Override
    public String toString() {
        return "User{id=" + id + ", userName='" + userName + "'}";
    }
}