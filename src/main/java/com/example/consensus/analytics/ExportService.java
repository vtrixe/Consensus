package com.example.consensus.analytics;

import com.example.consensus.analytics.dto.ExportJobResponse;
import com.example.consensus.analytics.dto.ExportRequest;

public interface ExportService {
    ExportJobResponse submit(ExportRequest request);
    ExportJobResponse status(String jobId);
    void processAsync(String jobId, String tenantSchema);
    byte[] download(String jobId);
}
