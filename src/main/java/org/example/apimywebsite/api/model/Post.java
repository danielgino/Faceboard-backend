package org.example.apimywebsite.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long postId;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "post_content")
    private String postText;
    private LocalDateTime createdAt;
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    private List<Like> likes;

//    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
//    private List<Comment> comments;
//    // Getters & Setters
//    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<PostImage> images = new ArrayList<>();
@BatchSize(size = 16)
@OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
private List<PostImage> images;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Comment> comments = new ArrayList<>();

    @Column(nullable = false)
    private boolean edited = false;


}


