package com.example.consensus.analytics;

import com.example.consensus.analytics.dto.ExportJobResponse;
import com.example.consensus.analytics.dto.ExportRequest;
import com.example.consensus.analytics.excel.OpsReportBuilder;
import com.example.consensus.analytics.excel.SettlementRiskBuilder;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.entity.TradeBreakAudit;
import com.example.consensus.model.repository.TradeBreakAuditRepository;
import com.example.consensus.model.repository.TradeBreakRepository;
import com.example.consensus.model.repository.TradeRepository;
import com.example.consensus.tenant.TenantContext;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final ExportJobRepository exportJobRepository;
    private final TradeBreakRepository tradeBreakRepository;
    private final TradeBreakAuditRepository tradeBreakAuditRepository;
    private final TradeRepository tradeRepository;
    private final JavaMailSender mailSender;
    private final ExportStorageService storageService;
    private final OpsReportBuilder opsReportBuilder;
    private final SettlementRiskBuilder settlementRiskBuilder;

    @Override
    public ExportJobResponse submit(ExportRequest request) {
        ExportJob job = new ExportJob();
        job.setId(UUID.randomUUID().toString());
        job.setModelType(request.getModelType());
        job.setStatus("QUEUED");
        job.setRecipientEmail(request.getRecipientEmail());
        job.setTenantSchema(TenantContext.get());
        job.setCreatedAt(LocalDateTime.now());
        exportJobRepository.save(job);
        return new ExportJobResponse(job.getId(), job.getStatus(), job.getModelType());
    }

    @Override
    public ExportJobResponse status(String jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        return new ExportJobResponse(job.getId(), job.getStatus(), job.getModelType());
    }

    @Override
    @Async
    public void processAsync(String jobId, String tenantSchema) {
        TenantContext.set(tenantSchema);
        try {
            ExportJob job = exportJobRepository.findById(jobId).orElseThrow();
            job.setStatus("PROCESSING");
            exportJobRepository.save(job);

            byte[] excelBytes = buildExcel(job);

            String filename = job.getModelType().toLowerCase() + "_" + LocalDate.now() + ".xlsx";
            String fileKey = storageService.upload(filename, excelBytes);
            job.setFileKey(fileKey);

            sendEmail(job.getRecipientEmail(), job.getModelType(), excelBytes);

            job.setStatus("DONE");
            job.setCompletedAt(LocalDateTime.now());
            exportJobRepository.save(job);
            log.info("Export job {} complete for tenant={}", jobId, tenantSchema);
        } catch (Exception e) {
            log.error("Export job {} failed", jobId, e);
            exportJobRepository.findById(jobId).ifPresent(j -> {
                j.setStatus("FAILED");
                j.setErrorMessage(e.getMessage());
                j.setCompletedAt(LocalDateTime.now());
                exportJobRepository.save(j);
            });
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public byte[] download(String jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        if (job.getFileKey() == null) throw new RuntimeException("File not available for job: " + jobId);
        try { return storageService.download(job.getFileKey()); }
        catch (Exception e) { throw new RuntimeException("Download failed", e); }
    }

    private byte[] buildExcel(ExportJob job) throws Exception {
        XSSFWorkbook wb;
        if ("OPS_REPORT".equals(job.getModelType())) {
            List<TradeBreak> breaks = tradeBreakRepository.findAll();
            List<TradeBreakAudit> audits = tradeBreakAuditRepository.findAll();
            wb = opsReportBuilder.build(breaks, audits);
        } else if ("SETTLEMENT_RISK".equals(job.getModelType())) {
            List<TradeBreak> breaks = tradeBreakRepository.findAll();
            List<Trade> trades = tradeRepository.findAll();
            wb = settlementRiskBuilder.build(breaks, trades);
        } else {
            throw new IllegalArgumentException("Unknown model type: " + job.getModelType());
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            wb.close();
            return out.toByteArray();
        }
    }

    private void sendEmail(String to, String modelType, byte[] excelBytes) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true);
        helper.setTo(to);
        helper.setSubject("[Consensus] " + modelType.replace("_", " ") + " — " + LocalDate.now());
        helper.setText("Please find attached your requested " + modelType.replace("_", " ") + " report.");
        String filename = modelType.toLowerCase() + "_" + LocalDate.now() + ".xlsx";
        helper.addAttachment(filename, new ByteArrayResource(excelBytes),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mailSender.send(msg);
    }
}
