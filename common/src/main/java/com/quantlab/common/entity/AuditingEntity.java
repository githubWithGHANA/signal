package com.quantlab.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public class AuditingEntity implements Serializable {

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delete_indicator")
    private String deleteIndicator;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        createdBy = "system";  // Use your logic to fetch the current user
        updatedBy = createdBy;
        deleteIndicator = "N"; // Default to "N" when the entity is first created
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
        updatedBy = "system";
    }
}
