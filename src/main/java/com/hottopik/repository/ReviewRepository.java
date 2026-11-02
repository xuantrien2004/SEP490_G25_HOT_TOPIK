package com.hottopik.repository;

import com.hottopik.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Dùng JOIN FETCH để load luôn thông tin User, tránh tuyệt đối lỗi LazyInitializationException
    @Query("SELECT r FROM Review r JOIN FETCH r.user WHERE LOWER(r.status) = LOWER(:status) ORDER BY r.createdAt DESC")
    List<Review> findByStatusWithUser(String status);
}