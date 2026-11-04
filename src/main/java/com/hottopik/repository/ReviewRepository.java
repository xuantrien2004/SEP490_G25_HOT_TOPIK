package com.hottopik.repository;

import com.hottopik.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Dùng JOIN FETCH để load luôn thông tin User, tránh tuyệt đối lỗi LazyInitializationException (Dùng cho danh sách đầy đủ)
    @Query("SELECT r FROM Review r JOIN FETCH r.user WHERE LOWER(r.status) = LOWER(:status) ORDER BY r.createdAt DESC")
    List<Review> findByStatusWithUser(@Param("status") String status);

    // Hỗ trợ phân trang cho toàn bộ review theo trạng thái kèm JOIN FETCH user
    @Query(value = "SELECT r FROM Review r JOIN FETCH r.user WHERE LOWER(r.status) = LOWER(:status)",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE LOWER(r.status) = LOWER(:status)")
    Page<Review> findByStatusWithUserPage(@Param("status") String status, Pageable pageable);

    // Hỗ trợ phân trang VÀ lọc theo số sao kèm JOIN FETCH user
    @Query(value = "SELECT r FROM Review r JOIN FETCH r.user WHERE LOWER(r.status) = LOWER(:status) AND r.rating = :rating",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE LOWER(r.status) = LOWER(:status) AND r.rating = :rating")
    Page<Review> findByStatusAndRating(@Param("status") String status, @Param("rating") Short rating, Pageable pageable);
}