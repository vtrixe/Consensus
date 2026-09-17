package com.example.consensus.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "export_jobs")
@Data
@NoArgsConstructor
public class ExportJob {

    @Id
    private String id;

    @Column(name = "model_type")
    private String modelType;

    private String status;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "tenant_schema")
    private String tenantSchema;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "error_message")
    private String errorMessage;
}
