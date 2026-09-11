package com.example.consensus.reconciliation;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.repository.TradeBreakRepository;
import com.example.consensus.model.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final TradeRepository tradeRepository;
    private final TradeBreakRepository tradeBreakRepository;
    private final MaterialityScorer  materialityScorer;

    @Override
    public List<TradeBreak> reconcile() {
        List<TradeBreak> breaks = new ArrayList<>();

        List<Trade> blotterTrades = tradeRepository.findBySource(DataSource.BLOTTER);
        Set<String> blotterTradeIds = blotterTrades.stream()
                .map(Trade::getTradeId)
                .collect(Collectors.toSet());

        for (Trade blotter : blotterTrades) {
            Optional<Trade> custodianOpt = tradeRepository
                    .findByTradeIdAndSource(blotter.getTradeId(), DataSource.CUSTODIAN);

            if (custodianOpt.isEmpty()) {
                Long estimatedMinutestoResolution = materialityScorer.estimateResolutionMinutes(BreakType.MISSING_CUSTODIAN, blotter.getCounterparty(),blotter.getSymbol(),blotter.getQuantity());
                breaks.add(newBreak(blotter.getTradeId(), BreakType.MISSING_CUSTODIAN, null, null,null, estimatedMinutestoResolution));
            } else {
                Trade cust = custodianOpt.get();
                if (!withinTolerance(blotter, cust, BreakType.PRICE_MISMATCH)) {
                    Long estimatedMinutestoResolution = materialityScorer.estimateResolutionMinutes(BreakType.PRICE_MISMATCH, blotter.getCounterparty(),blotter.getSymbol(),blotter.getQuantity());
                    breaks.add(newBreak(
                            blotter.getTradeId(),
                            BreakType.PRICE_MISMATCH,
                            blotter.getPrice().toString(),
                            cust.getPrice().toString(),
                            calculateBpsDeviation(blotter, cust),
                            estimatedMinutestoResolution
                    ));
                }

                if (!withinTolerance(blotter, cust, BreakType.QUANTITY_MISMATCH)) {
                    Long estimatedMinutestoResolution = materialityScorer.estimateResolutionMinutes(BreakType.QUANTITY_MISMATCH, blotter.getCounterparty(),blotter.getSymbol(),blotter.getQuantity());
                    breaks.add(newBreak(
                            blotter.getTradeId(),
                            BreakType.QUANTITY_MISMATCH,
                            blotter.getQuantity().toString(),
                            cust.getQuantity().toString(),
                            calculateBpsDeviation(blotter, cust),
                            estimatedMinutestoResolution
                    ));
                }

                if (!blotter.getSettlementDate().equals(cust.getSettlementDate())) {
                    Long estimatedMinutestoResolution = materialityScorer.estimateResolutionMinutes(BreakType.SETTLEMENT_DATE_MISMATCH, blotter.getCounterparty(),blotter.getSymbol(),blotter.getQuantity());
                    breaks.add(newBreak(blotter.getTradeId(), BreakType.SETTLEMENT_DATE_MISMATCH,
                            blotter.getSettlementDate().toString(), cust.getSettlementDate().toString(), null, estimatedMinutestoResolution));
                }

                if (!blotter.getCounterparty().equals(cust.getCounterparty())) {
                    Long estimatedMinutestoResolution = materialityScorer.estimateResolutionMinutes(BreakType.COUNTERPARTY_MISMATCH, blotter.getCounterparty(),blotter.getSymbol(),blotter.getQuantity());
                    breaks.add(newBreak(blotter.getTradeId(), BreakType.COUNTERPARTY_MISMATCH,
                            blotter.getCounterparty(), cust.getCounterparty(), null,estimatedMinutestoResolution));
                }
            }
        }

        tradeRepository.findBySource(DataSource.CUSTODIAN).stream()
                .filter(c -> !blotterTradeIds.contains(c.getTradeId()))
                .map(c -> {
                    long estimated = materialityScorer.estimateResolutionMinutes(
                            BreakType.MISSING_BLOTTER,
                            c.getCounterparty(),
                            c.getSymbol(),
                            c.getQuantity()
                    );
                    return newBreak(c.getTradeId(), BreakType.MISSING_BLOTTER, null, null, null, estimated);
                })
                .forEach(breaks::add);

        log.info("Reconciliation complete — {} break(s) found", breaks.size());
        return breaks;
    }

    @Override
    public List<TradeBreak> findBreaks(BreakStatus status) {
        return status != null
                ? tradeBreakRepository.findAllByStatus(status)
                : tradeBreakRepository.findAll();
    }

    @Override
    public List<TradeBreak> findBreaksByTradeId(String tradeId) {
        return tradeBreakRepository.findAllByTradeId(tradeId);
    }

    private TradeBreak newBreak(String tradeId, BreakType type,
                                String blotterValue, String custodianValue,
                                BigDecimal bpsDeviation, Long estimatedResolutionMinutes) {

        TradeBreak tradeBreak = new TradeBreak()
                .setTradeId(tradeId)
                .setBreakType(type)
                .setBlotterValue(blotterValue)
                .setCustodianValue(custodianValue)
                .setDetectedAt(LocalDateTime.now())
                .setStatus(BreakStatus.OPEN)
                .setBpsDeviation(bpsDeviation)
                .setEstimatedResolutionMinutes(estimatedResolutionMinutes);

        // Step 1: notional impact + old threshold tier + sets settlementDate
        materialityScorer.notion(tradeBreak);
        // Step 2: composite score (urgency + ADV + notional + counterparty) + sets minutesToSettlement
        tradeBreak.setCompositeScore(materialityScorer.scoreTradeBreak(tradeBreak));
        // Step 3: feasibility gate — can this break be resolved before settlement cutoff?
        Long minutesLeft = tradeBreak.getMinutesToSettlement();
        boolean failRisk = minutesLeft != null
                && estimatedResolutionMinutes != null
                && minutesLeft < estimatedResolutionMinutes;
        tradeBreak.setSettlementFailRisk(failRisk);

        return tradeBreakRepository.save(tradeBreak);
    }

    private BigDecimal calculateBpsDeviation(Trade a, Trade b) {
        if (a.getPrice() == null || b.getPrice() == null || a.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = a.getPrice().subtract(b.getPrice()).abs();
        return diff.divide(a.getPrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(10_000)); // fraction → basis points
    }

    private boolean withinTolerance(Trade a, Trade b, BreakType type) {
        if (BreakType.PRICE_MISMATCH.equals(type)) {
            BigDecimal bpsDeviation = calculateBpsDeviation(a, b);
            return bpsDeviation.compareTo(BigDecimal.valueOf(2)) <= 0; // 0.0002 * 10,000 = 2 bps
        }

        if (BreakType.QUANTITY_MISMATCH.equals(type)) {
            BigDecimal diff = a.getQuantity().subtract(b.getQuantity()).abs();
            return diff.compareTo(new BigDecimal("100")) <= 0;
        }

        return false;
    }


}
