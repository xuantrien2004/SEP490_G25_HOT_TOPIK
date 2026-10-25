package com.hottopik.repository;

import com.hottopik.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    // Lấy danh sách review đã được duyệt để hiển thị ngoài trang chủ/cẩm nang
    List<Review> findByStatusOrderByCreatedAtDesc(String status);
}