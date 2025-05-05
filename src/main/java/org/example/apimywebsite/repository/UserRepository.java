package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {
    User findByEmail(String email);
    List<User> findByNameContaining(String name);
    User findByUserName(String userName);


}
