package com.example.consensus.analytics.dto;

import lombok.Data;

@Data
public class ExportRequest {
    private String modelType;       // OPS_REPORT or SETTLEMENT_RISK
    private String recipientEmail;
}
