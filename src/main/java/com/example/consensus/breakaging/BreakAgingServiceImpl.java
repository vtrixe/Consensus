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
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Component
@Service
@RequiredArgsConstructor
@Slf4j
public class BreakAgingServiceImpl implements BreakAgingService {

    private final TradeBreakRepository tradeBreakRepository;
    private final TradeBreakAuditRepository tradeBreakAuditRepository;



    @Override
    public void transitionState(Long breakId, BreakStatus newStatus, String assignedTo, String notes){

        TradeBreak tradeBreak = tradeBreakRepository.findById(breakId)
                .orElseThrow(() -> new RuntimeException("TradeBreak not found: " + breakId));

        TradeBreakAudit tradeBreakAudit = new TradeBreakAudit();

        tradeBreakAudit.setId(tradeBreak.getId());

        if (BreakStatus.OPEN.equals(tradeBreak.getStatus())) {
            switch (newStatus) {
                case OPEN:
                    log.warn("skipping as status already open  trade break for  {}", breakId);
                    break;
                case WRITTEN_OFF:
                    if(tradeBreak.getSettlementFailRisk() || MaterialityTier.CRITICAL.equals(tradeBreak.getMaterialityTier())){
                        log.error("BreakAgingServiceImpl transitionState - settlement failed as cannot transfer to WRITTEN_OFF");
                    }
                    else{
                        newTradeBreakAudit(tradeBreak,BreakStatus.OPEN,BreakStatus.WRITTEN_OFF, notes, assignedTo);
                    }
                    break;
                case INVESTIGATING:
                    if(StringUtils.isBlank(assignedTo) || "SYSTEM".equals(assignedTo)){
                    log.error("BreakAgingServiceImpl transitionState - settlement failed as cannot transfer INVESTIGATING to a SYSTEM");
                    }
                    else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.OPEN, BreakStatus.INVESTIGATING, notes, assignedTo);
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
                    log.warn("skipping as status already open  trade break for  {}", breakId);
                    break;
                case OPEN:
                    newTradeBreakAudit(tradeBreak,BreakStatus.INVESTIGATING,BreakStatus.OPEN, notes, assignedTo);
                case PENDING_CONFIRM:
                    newTradeBreakAudit(tradeBreak,BreakStatus.INVESTIGATING,BreakStatus.PENDING_CONFIRM, notes, assignedTo);
                    break;
                case WRITTEN_OFF:
                    if(tradeBreak.getSettlementFailRisk() || MaterialityTier.CRITICAL.equals(tradeBreak.getMaterialityTier())){
                        log.error("BreakAgingServiceImpl transitionState - settlement failed as cannot transfer to WRITTEN_OFF");
                    }
                    else {
                        newTradeBreakAudit(tradeBreak, BreakStatus.INVESTIGATING, BreakStatus.WRITTEN_OFF, notes, assignedTo);
                    }
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
                    newTradeBreakAudit(tradeBreak,BreakStatus.PENDING_CONFIRM,BreakStatus.INVESTIGATING, notes, assignedTo);
                    break;
                case RESOLVED:
                    newTradeBreakAudit(tradeBreak,BreakStatus.PENDING_CONFIRM,BreakStatus.RESOLVED, notes, assignedTo);
                    break;
                default:
                    log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                    break;
            }
        }
        if(BreakStatus.RESOLVED.equals(tradeBreak.getStatus())){
            switch (newStatus) {
                case OPEN:
                    newTradeBreakAudit(tradeBreak,BreakStatus.RESOLVED,BreakStatus.OPEN, notes, assignedTo);
                    break;
                    default:
                        log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                        break;
            }
        }
        if(BreakStatus.RESOLVED.equals(tradeBreak.getStatus())){
            switch (newStatus) {
                default:
                    log.error("BreakAgingServiceImpl transitionState - inapplicable break status: " + newStatus);
                    break;

            }
        }



    }


        private void newTradeBreakAudit(TradeBreak tradeBreak, BreakStatus fromStatus, BreakStatus toStatus, String notes, String assignedTo) {
        Optional<TradeBreakAudit> tradeBreakAudit = tradeBreakAuditRepository.findFirstByTradeBreak_IdOrderByIdDesc(tradeBreak.getId());
        if(Objects.isNull(tradeBreakAudit)){
        }
        }

}
