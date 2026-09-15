package com.example.consensus.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TenantRegisterResponse {
    private String name;
    private String schemaName;
    private String apiKey;
}
