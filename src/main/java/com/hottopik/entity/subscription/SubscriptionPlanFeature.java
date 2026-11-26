package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Quyền lợi thuộc một phiên bản gói học (bất biến cùng phiên bản). */
@Entity
@Table(name = "subscription_plan_features", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_plan_features_version_feature", columnNames = {"plan_version_id", "feature_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_version_id", nullable = false, foreignKey = @ForeignKey(name = "FK_plan_features_version"))
    private SubscriptionPlanVersion planVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_id", nullable = false, foreignKey = @ForeignKey(name = "FK_plan_features_feature"))
    private SubscriptionFeature feature;

    /** Hạn mức (nếu quyền lợi có giới hạn số lượt). null = theo mặc định của tính năng. */
    @Column(name = "limit_value")
    private Integer limitValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = PaymentDateTimeUtil.now();
    }
}
