package com.english.education.model.repository.user;

import com.english.education.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    @Query("""
                SELECT u FROM User u
                JOIN FETCH u.roles
                WHERE u.username = :username
                  AND u.deletedAt IS NULL
            """)
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByIdAndDeletedAtIsNull(Integer id);
}
