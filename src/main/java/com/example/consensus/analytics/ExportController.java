package com.example.consensus.analytics;

import com.example.consensus.analytics.dto.ExportJobResponse;
import com.example.consensus.analytics.dto.ExportRequest;
import com.example.consensus.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/analytics/export")
@RequiredArgsConstructor
@Tag(name = "Analytics & Export", description = "Queue Excel export jobs and retrieve or download the generated files")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "ApiKeyAuth")
public class ExportController {

    private final ExportService exportService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Submit export job",
        description = """
            Queues an async Excel export. Returns immediately with a jobId.
            Poll `/status` until `status=DONE`, then call `/download` or check MailHog for the emailed file.

            **modelType values:**
            - `OPS_REPORT` — break KPIs with cross-sheet COUNTIF/SUMIF formulas (RAW_DATA + SUMMARY sheets)
            - `SETTLEMENT_RISK` — CSDR penalty positions with At Risk flags (POSITIONS + SUMMARY sheets)
            """
    )
    @ApiResponse(responseCode = "202", description = "Job accepted — use jobId to poll status")
    @ApiResponse(responseCode = "401", description = "X-Api-Key missing")
    public ExportJobResponse submit(@RequestBody ExportRequest request) {
        String schema = TenantContext.get();
        if (schema == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Api-Key required");
        ExportJobResponse resp = exportService.submit(request);
        exportService.processAsync(resp.getJobId(), schema);
        return resp;
    }

    @GetMapping("/{jobId}/status")
    @Operation(summary = "Poll export job status", description = "Returns current job status: `QUEUED` → `PROCESSING` → `DONE` | `FAILED`.")
    @ApiResponse(responseCode = "200", description = "Job status returned")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ExportJobResponse status(@Parameter(description = "Job ID from submit response") @PathVariable String jobId) {
        return exportService.status(jobId);
    }

    @GetMapping("/{jobId}/download")
    @Operation(summary = "Download Excel file", description = "Streams the generated `.xlsx` file. Job must be in `DONE` status.")
    @ApiResponse(responseCode = "200", description = "Excel file stream (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)")
    @ApiResponse(responseCode = "404", description = "Job not found or not yet complete")
    public ResponseEntity<byte[]> download(@Parameter(description = "Job ID") @PathVariable String jobId) {
        byte[] bytes = exportService.download(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export_" + jobId + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
