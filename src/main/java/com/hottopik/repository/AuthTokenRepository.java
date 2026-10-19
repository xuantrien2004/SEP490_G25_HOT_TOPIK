package com.hottopik.repository;

import com.hottopik.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenHash(String tokenHash);
    Optional<AuthToken> findFirstByUserIdAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(Long userId, String type);
}
