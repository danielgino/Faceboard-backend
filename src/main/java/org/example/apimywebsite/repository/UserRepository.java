package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Integer> {
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.userName = :userName")
    List<User> findByNameContaining(String name);
    @Query("SELECT u FROM User u WHERE u.userName = :userName")
    User findByUserName(@Param("userName") String userName);

    @Query("""
    SELECT u FROM User u
    WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(u.lastname) LIKE LOWER(CONCAT('%', :query, '%'))
       OR LOWER(CONCAT(u.name, ' ', u.lastname)) LIKE LOWER(CONCAT('%', :query, '%'))
""")
    List<User> searchByFullName(@Param("query") String query);
}
