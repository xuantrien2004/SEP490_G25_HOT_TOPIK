package com.hottopik.repository;

import com.hottopik.entity.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {
    List<EmailOutbox> findByStatusOrderByCreatedAtAsc(String status);
}
