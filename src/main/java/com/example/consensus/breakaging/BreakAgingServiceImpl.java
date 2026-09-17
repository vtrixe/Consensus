package com.example.consensus.breakaging;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.MaterialityTier;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.entity.TradeBreakAudit;
import com.example.consensus.model.repository.TradeBreakAuditRepository;
import com.example.consensus.model.repository.TradeBreakRepository;
import com.example.consensus.tenant.TenantContext;
import com.example.consensus.worker.events.BreakStatusChanged;
import com.example.consensus.worker.producer.ProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import static java.lang.Boolean.TRUE;

@Service
@RequiredArgsConstructor
@Slf4j
public class BreakAgingServiceImpl implements BreakAgingService {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    private final TradeBreakRepository tradeBreakRepository;
    private final TradeBreakAuditRepository tradeBreakAuditRepository;
    private final ProducerService producerService;

    @Override
    public void transitionState(Long breakId, BreakStatus newStatus, String changedBy, String assignedTo, String notes){

        TradeBreak tradeBreak = tradeBreakRepository.findById(breakId)
                .orElseThrow(() -> new RuntimeException("TradeBreak not found: " + breakId));

        if (BreakStatus.OPEN.equals(tradeBreak.getStatus())) {
            switch (newStatus) {
                case OPEN:
                    log.warn("skipping as status already open  trade break for  {}", breakId);
                    break;
                case WRITTEN_OFF:
                    if(tradeBreak.getSettlementFailRisk() || MaterialityTier.CRITICAL.equals(tradeBreak.getMaterialityTier()) || StringUtils.isEmpty(notes)) {
                        log.error("BreakAgingServiceImpl transitionState - settlement failed as cannot transfer to WRITTEN_OFF");
                    }
                    else{
                        newTradeBreakAudit(tradeBreak, BreakStatus.OPEN, BreakStatus.WRITTEN_OFF, changedBy, notes, assignedTo, null, null);
                    }
                    break;
                case INVESTIGATING:
                    if(!"SYSTEM".equals(changedBy) && (StringUtils.isBlank(assignedTo) || "SYSTEM".equals(assignedTo))){
                        log.error("BreakAgingServiceImpl transitionState - human assignee required to claim INVESTIGATING");
                    }
                    else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.OPEN, BreakStatus.INVESTIGATING, changedBy, notes, assignedTo,null, null);
                    }
                    break;
                case RESOLVED:
                    // SYSTEM only: AUTO_MATCH bypass — humans must go through INVESTIGATING → PENDING_CONFIRM first
                    if (!"SYSTEM".equals(changedBy)) {
                        log.error("BreakAgingServiceImpl transitionState - OPEN → RESOLVED only allowed for SYSTEM");
                    } else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.OPEN, BreakStatus.RESOLVED, changedBy, notes, assignedTo, null, null);
                    }
                    break;
                    default:
                        log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                        break;

            }
        }
        if (BreakStatus.INVESTIGATING.equals(tradeBreak.getStatus())) {
            switch (newStatus) {
                case INVESTIGATING:
                    log.warn("skipping as status already investigating trade break for  {}", breakId);
                    break;
                case OPEN:
                    newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.OPEN, changedBy, notes, assignedTo, null, null);
                    break;
                case PENDING_CONFIRM:
                    newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.PENDING_CONFIRM, changedBy, notes, assignedTo, null, null);
                    break;
                case WRITTEN_OFF:
                    if(tradeBreak.getSettlementFailRisk() || MaterialityTier.CRITICAL.equals(tradeBreak.getMaterialityTier()) || StringUtils.isEmpty(notes)){
                        log.error("BreakAgingServiceImpl transitionState - settlement failed as cannot transfer to WRITTEN_OFF");
                    }
                    else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.WRITTEN_OFF, changedBy, notes, assignedTo, null, null);
                    }
                    break;
                case RESOLVED:
                    if (!"SYSTEM".equals(changedBy)) {
                        log.error("BreakAgingServiceImpl transitionState - INVESTIGATING → RESOLVED only allowed for SYSTEM; use PENDING_CONFIRM first");
                    } else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.RESOLVED, changedBy, notes, assignedTo, null, null);
                    }
                    break;
                    default:
                        log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                        break;
            }
        }
        if(BreakStatus.PENDING_CONFIRM.equals(tradeBreak.getStatus())){
            switch (newStatus) {
                case PENDING_CONFIRM:
                    log.warn("skipping as status already under pending confirmation  trade break for  {}", breakId);
                    break;
                case INVESTIGATING:
                    newTradeBreakAudit(tradeBreak, BreakStatus.PENDING_CONFIRM, BreakStatus.INVESTIGATING, changedBy, notes, assignedTo, null, null);
                    break;
                case RESOLVED:
                    newTradeBreakAudit(tradeBreak, BreakStatus.PENDING_CONFIRM, BreakStatus.RESOLVED, changedBy, notes, assignedTo, null, null);
                    break;
                default:
                    log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                    break;
            }
        }
        if(BreakStatus.RESOLVED.equals(tradeBreak.getStatus())){
            switch (newStatus) {
                case OPEN:
                    newTradeBreakAudit(tradeBreak, BreakStatus.RESOLVED, BreakStatus.OPEN, changedBy, notes, assignedTo, null, null);
                    break;
                    default:
                        log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                        break;
            }
        }
        if(BreakStatus.WRITTEN_OFF.equals(tradeBreak.getStatus())){
            switch (newStatus) {
                default:
                    log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                    break;
            }
        }
    }

    @Override
    @Scheduled(fixedRate = 900000)
    public void escalateStaleBreaks() {

        List<TradeBreak> currBreaks = tradeBreakRepository.findAllByStatusIn(List.of(BreakStatus.OPEN, BreakStatus.INVESTIGATING));

        for (TradeBreak tradeBreak : currBreaks) {

            // settlementFailRisk: force any OPEN break to INVESTIGATING regardless of tier
            if (TRUE.equals(tradeBreak.getSettlementFailRisk()) && BreakStatus.OPEN.equals(tradeBreak.getStatus())) {
                transitionState(tradeBreak.getId(), BreakStatus.INVESTIGATING, "SYSTEM", "SYSTEM",
                        "Auto-escalated: settlement fail risk — pending human assignment");
                continue;
            }

            Long hoursPassed;
            if (tradeBreak.getStatus().equals(BreakStatus.OPEN)) {
                hoursPassed = Duration.between(tradeBreak.getDetectedAt(), LocalDateTime.now(ET)).toHours();
            } else {
                hoursPassed = Duration.between(tradeBreak.getLastChangeAt(), LocalDateTime.now(ET)).toHours();
            }

            incrementMateriality(tradeBreak, hoursPassed);
        }

    }

    @Override
    @Scheduled(cron = "0 0 15 * * *", zone = "America/New_York")
    public void pendingConfirmSweep() {
        List<TradeBreak> currPendingBreaks = tradeBreakRepository.findAllByStatusIn(List.of(BreakStatus.PENDING_CONFIRM));
        for (TradeBreak tradeBreak : currPendingBreaks) {
            transitionState(tradeBreak.getId(),BreakStatus.INVESTIGATING,"SYSTEM",tradeBreak.getAssignedTo(),"Sweeping to Investigation due to pending stuck at EOD ET");
        }
    }


    private void newTradeBreakAudit(TradeBreak tradeBreak, BreakStatus fromStatus, BreakStatus toStatus,
                                    String changedBy, String notes, String assignedTo, Boolean slaBreached, MaterialityTier tier) {

        LocalDateTime now = LocalDateTime.now(ET);

        LocalDateTime baseline = tradeBreak.getLastChangeAt() != null
                ? tradeBreak.getLastChangeAt()
                : tradeBreak.getDetectedAt();
        long durationMinutes = Duration.between(baseline, now).toMinutes();

        TradeBreakAudit currTradeBreakAudit = new TradeBreakAudit();
        currTradeBreakAudit.setTradeBreak(tradeBreak);
        currTradeBreakAudit.setFromStatus(fromStatus);
        currTradeBreakAudit.setToStatus(toStatus);
        currTradeBreakAudit.setChangedAt(now);
        currTradeBreakAudit.setChangedBy(changedBy);
        currTradeBreakAudit.setDuration(durationMinutes);
        currTradeBreakAudit.setNotes(notes);
        tradeBreakAuditRepository.save(currTradeBreakAudit);

        tradeBreak.setStatus(toStatus);
        tradeBreak.setLastChangeAt(now);

        if (toStatus == BreakStatus.INVESTIGATING) {
            tradeBreak.setAssignedTo(assignedTo);
            if (tradeBreak.getInvestigationStartedAt() == null) {
                tradeBreak.setInvestigationStartedAt(now);
            }
        }

        if (toStatus == BreakStatus.RESOLVED || toStatus == BreakStatus.WRITTEN_OFF) {
            tradeBreak.setResolvedAt(now);
        }

        if (fromStatus == BreakStatus.RESOLVED && toStatus == BreakStatus.OPEN) {
            tradeBreak.setAssignedTo(null);
            tradeBreak.setInvestigationStartedAt(null);
        }
        if(BooleanUtils.isTrue(slaBreached)){
            tradeBreak.setSlaBreached(true);
        }

        if(Objects.nonNull(tier)){
            tradeBreak.setMaterialityTier(tier);
        }

       TradeBreak saved =  tradeBreakRepository.save(tradeBreak);

        try {
            BreakStatusChanged detectedEvent = new BreakStatusChanged();
            detectedEvent.setBreakId(saved.getId());
            detectedEvent.setFromStatus(fromStatus);
            detectedEvent.setToStatus(toStatus);
            detectedEvent.setChangedBy(changedBy);
            detectedEvent.setTenantSchema(TenantContext.get());

            producerService.publishBreakStatusChanged(detectedEvent);
        } catch (Exception e) {
            log.error(
                    "Failed to publish BreakDetectedEvent for tradeBreakId={}",
                    saved.getId(),
                    e
            );
        }
        log.info("Break {}: {} → {} by {} ({}min in prior state)", tradeBreak.getId(), fromStatus, toStatus, changedBy, durationMinutes);
    }

    private void incrementMateriality(TradeBreak currBreak, Long hoursPassed) {
        switch (currBreak.getMaterialityTier()) {
            case CRITICAL:
                // Already at ceiling — no tier to promote, just record the breach
                if (currBreak.getStatus().equals(BreakStatus.OPEN)) {
                    if (hoursPassed > 4) {
                        newTradeBreakAudit(currBreak, currBreak.getStatus(), currBreak.getStatus(), "SYSTEM", "SLA breached: already at CRITICAL tier", null, TRUE, null);
                    }
                } else {
                    if (hoursPassed > 2) {
                        newTradeBreakAudit(currBreak, currBreak.getStatus(), currBreak.getStatus(), "SYSTEM", "SLA breached: already at CRITICAL tier", currBreak.getAssignedTo(), TRUE, null);
                    }
                }
                break;
            case MAJOR:
                if(currBreak.getStatus().equals(BreakStatus.OPEN)){
                    if(hoursPassed > 8){
                        newTradeBreakAudit(currBreak, currBreak.getStatus(),currBreak.getStatus(),"SYSTEM","Escalating due to SLA time breach",null,TRUE, MaterialityTier.CRITICAL);
                    }
                }
                else {
                    if(hoursPassed > 4) {
                        newTradeBreakAudit(currBreak, currBreak.getStatus(), currBreak.getStatus(), "SYSTEM", "Escalating due to SLA time breach", currBreak.getAssignedTo(), TRUE, MaterialityTier.CRITICAL);
                    }
                }
                break;

            case MINOR :
                if(currBreak.getStatus().equals(BreakStatus.OPEN)){
                    if(hoursPassed > 24){
                        newTradeBreakAudit(currBreak, currBreak.getStatus(),currBreak.getStatus(),"SYSTEM","Escalating due to SLA time breach",null,TRUE, MaterialityTier.MAJOR);
                    }
                }
                else {
                    if(hoursPassed > 8) {
                        newTradeBreakAudit(currBreak, currBreak.getStatus(), currBreak.getStatus(), "SYSTEM", "Escalating due to SLA time breach", currBreak.getAssignedTo(), TRUE, MaterialityTier.MAJOR);
                    }
                }
                break;

                default:
                    log.error("BreakAgingServiceImpl transitionState - inapplicable");
                    break;
        }
    }
}
