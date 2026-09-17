package com.example.consensus.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExportJobResponse {
    private String jobId;
    private String status;
    private String modelType;
}
