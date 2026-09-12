package com.example.consensus.breakaging;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.MaterialityTier;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.entity.TradeBreakAudit;
import com.example.consensus.model.repository.TradeBreakAuditRepository;
import com.example.consensus.model.repository.TradeBreakRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class BreakAgingServiceImpl implements BreakAgingService {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    private final TradeBreakRepository tradeBreakRepository;
    private final TradeBreakAuditRepository tradeBreakAuditRepository;



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
                        newTradeBreakAudit(tradeBreak, BreakStatus.OPEN, BreakStatus.WRITTEN_OFF, changedBy, notes, assignedTo);
                    }
                    break;
                case INVESTIGATING:
                    if(!"SYSTEM".equals(changedBy) && (StringUtils.isBlank(assignedTo) || "SYSTEM".equals(assignedTo))){
                        log.error("BreakAgingServiceImpl transitionState - human assignee required to claim INVESTIGATING");
                    }
                    else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.OPEN, BreakStatus.INVESTIGATING, changedBy, notes, assignedTo);
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
                    newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.OPEN, changedBy, notes, assignedTo);
                    break;
                case PENDING_CONFIRM:
                    newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.PENDING_CONFIRM, changedBy, notes, assignedTo);
                    break;
                case WRITTEN_OFF:
                    if(tradeBreak.getSettlementFailRisk() || MaterialityTier.CRITICAL.equals(tradeBreak.getMaterialityTier()) || StringUtils.isEmpty(notes)){
                        log.error("BreakAgingServiceImpl transitionState - settlement failed as cannot transfer to WRITTEN_OFF");
                    }
                    else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.WRITTEN_OFF, changedBy, notes, assignedTo);
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
                    newTradeBreakAudit(tradeBreak, BreakStatus.PENDING_CONFIRM, BreakStatus.INVESTIGATING, changedBy, notes, assignedTo);
                    break;
                case RESOLVED:
                    newTradeBreakAudit(tradeBreak, BreakStatus.PENDING_CONFIRM, BreakStatus.RESOLVED, changedBy, notes, assignedTo);
                    break;
                default:
                    log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                    break;
            }
        }
        if(BreakStatus.RESOLVED.equals(tradeBreak.getStatus())){
            switch (newStatus) {
                case OPEN:
                    newTradeBreakAudit(tradeBreak, BreakStatus.RESOLVED, BreakStatus.OPEN, changedBy, notes, assignedTo);
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
    public void escalateStaleBreaks() {
        // TODO: scheduler — implemented next
    }

    @Override
    public void pendingConfirmSweep() {
        // TODO: cron at 3 PM ET — implemented next
    }


    private void newTradeBreakAudit(TradeBreak tradeBreak, BreakStatus fromStatus, BreakStatus toStatus,
                                    String changedBy, String notes, String assignedTo) {

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

        tradeBreakRepository.save(tradeBreak);
        log.info("Break {}: {} → {} by {} ({}min in prior state)", tradeBreak.getId(), fromStatus, toStatus, changedBy, durationMinutes);
    }
}
