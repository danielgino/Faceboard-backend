package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.userName LIKE %:name%")
    List<User> findByNameContaining(@Param("name") String name);
    @Query("SELECT u FROM User u WHERE u.name LIKE %:name%")

    User findByUserName(String userName);


}
