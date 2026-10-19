package com.hottopik.repository.exam;

import com.hottopik.entity.exam.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findBySourceIdOrderByFilenameAsc(Long sourceId);

    Optional<MediaAsset> findBySourceIdAndFilename(Long sourceId, String filename);
}
