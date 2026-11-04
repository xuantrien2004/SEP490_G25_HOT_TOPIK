package com.hottopik.repository;
import com.hottopik.entity.QuestionRevisionRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface QuestionRevisionRecipientRepository extends JpaRepository<QuestionRevisionRecipient,Long> {
    Optional<QuestionRevisionRecipient> findByRevisionIdAndUserId(Long revisionId,Long userId);
}
