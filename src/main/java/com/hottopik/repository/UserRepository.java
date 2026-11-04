package com.hottopik.repository;

import com.hottopik.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleIn(List<String> roles);

    @Query(value = "SELECT COUNT(*) FROM dbo.subscriptions WHERE user_id = :userId AND status = 'ACTIVE' "
            + "AND starts_at <= :now AND (ends_at IS NULL OR ends_at > :now)", nativeQuery = true)
    long countActiveSubscriptions(Long userId, LocalDateTime now);
}
