package com.example.consensus.fuzzymatch;

import com.example.consensus.breakaging.BreakAgingService;
import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.Enums.MatchTier;
import com.example.consensus.model.dtos.ScoreBreakdown;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.entity.TradeMatchCandidate;
import com.example.consensus.model.repository.TradeBreakRepository;
import com.example.consensus.model.repository.TradeMatchCandidateRepository;
import com.example.consensus.model.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuzzyMatchingServiceImpl implements FuzzyMatchingService {

    private final TradeBreakRepository tradeBreakRepository;
    private final TradeRepository tradeRepository;
    private final TradeMatchCandidateRepository tradeMatchCandidateRepository;
    private final BreakAgingService breakAgingService;

    @Override
   public void  matchBreak(Long breakId){

        TradeBreak tradeBreak = tradeBreakRepository.findById(breakId)
                .orElseThrow(() -> new RuntimeException("Break not found"));

        if (!BreakType.MISSING_BLOTTER.equals(tradeBreak.getBreakType())
                && !BreakType.MISSING_CUSTODIAN.equals(tradeBreak.getBreakType())) {
            log.error("Break type mismatch");
            throw new IllegalArgumentException("Break type mismatch");
        }

        List<Trade> trades  = new ArrayList<>();

        Trade currTrade = new Trade();


        switch (tradeBreak.getBreakType()) {
            case MISSING_BLOTTER:
                currTrade = tradeRepository
                        .findByTradeIdAndSource(
                                tradeBreak.getTradeId(),
                                DataSource.CUSTODIAN
                        )
                        .orElseThrow(() -> new RuntimeException("Trade not found"));

                LocalDate settlementDate = currTrade.getSettlementDate();
                trades = tradeRepository.findPotentialMatchingTrades(
                        DataSource.BLOTTER,
                        currTrade.getSymbol(),
                        settlementDate.minusDays(1),
                        settlementDate.plusDays(1)
                );
                break;
            case MISSING_CUSTODIAN:
                currTrade = tradeRepository
                        .findByTradeIdAndSource(
                                tradeBreak.getTradeId(),
                                DataSource.BLOTTER
                        )
                        .orElseThrow(() -> new RuntimeException("Trade not found"));

                settlementDate = currTrade.getSettlementDate();
                trades = tradeRepository.findPotentialMatchingTrades(
                        DataSource.CUSTODIAN,
                        currTrade.getSymbol(),
                        settlementDate.minusDays(1),
                        settlementDate.plusDays(1)
                );
                break;
        }


        Trade finalCurrTrade = currTrade;
        // FIX: == not != (keep matching sides, reject mismatched)
        trades = trades.stream()
                .filter(trade -> trade.getSide() == finalCurrTrade.getSide())
                .filter(trade -> trade.getCurrency().equals(finalCurrTrade.getCurrency()))
                .toList();

        // Score all candidates, sort, keep top 3, then save once
        List<TradeMatchCandidate> candidates = new ArrayList<>();
        for (Trade trade : trades) {
            candidates.add(scoreMatch(trade, currTrade, tradeBreak));
        }

        candidates.sort(Comparator.comparing(TradeMatchCandidate::getConfidenceScore, Comparator.reverseOrder()));

        List<TradeMatchCandidate> top3 = candidates.stream().limit(3).toList();
        for (int i = 0; i < top3.size(); i++) {
            top3.get(i).setRank(i + 1);
        }
        tradeMatchCandidateRepository.saveAll(top3);

        // AUTO_MATCH: rank-1 candidate >= 85 → auto-resolve the break
        if (!top3.isEmpty() && top3.get(0).getMatchTier() == MatchTier.AUTO_MATCH) {
            breakAgingService.transitionState(
                    tradeBreak.getId(), BreakStatus.RESOLVED, "SYSTEM", null,
                    "Auto-resolved: fuzzy match candidate " + top3.get(0).getCandidateTradeId()
                    + " at confidence " + top3.get(0).getConfidenceScore()
            );
        }

    }

    private TradeMatchCandidate scoreMatch(Trade trade, Trade currTrade, TradeBreak tradeBreak) {
        Long quantityScore;

        BigDecimal qtyDeviation = trade.getQuantity()
                .subtract(currTrade.getQuantity())
                .abs()
                .divide(currTrade.getQuantity(), MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100));

        if (qtyDeviation.compareTo(BigDecimal.ZERO) == 0) {
            quantityScore = 40L;
        } else if (qtyDeviation.compareTo(BigDecimal.ONE) <= 0) {
            quantityScore = 30L;
        } else if (qtyDeviation.compareTo(BigDecimal.valueOf(5)) <= 0) {
            quantityScore = 15L;
        } else {
            quantityScore = 0L;
        }

        Long priceScore;

        BigDecimal priceDeviationBps = trade.getPrice()
                .subtract(currTrade.getPrice())
                .abs()
                .divide(currTrade.getPrice(), MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(10_000));

        if (priceDeviationBps.compareTo(BigDecimal.ZERO) == 0) {
            priceScore = 30L; // FIX: max for price is 30, not 40
        } else if (priceDeviationBps.compareTo(BigDecimal.valueOf(2)) <= 0) {
            priceScore = 30L;
        } else if (priceDeviationBps.compareTo(BigDecimal.valueOf(5)) <= 0) {
            priceScore = 20L;
        } else if (priceDeviationBps.compareTo(BigDecimal.valueOf(10)) <= 0) {
            priceScore = 10L;
        } else {
            priceScore = 0L;
        }

        Long settlementDateScore;

        long daysDiff = Math.abs(
                ChronoUnit.DAYS.between(
                        currTrade.getSettlementDate(),
                        trade.getSettlementDate()
                )
        );

        settlementDateScore = daysDiff == 0 ? 20L : 10L;

        Long counterpartyScore;

        String currCounterparty = currTrade.getCounterparty();
        String tradeCounterparty = trade.getCounterparty();

        if (currCounterparty.equalsIgnoreCase(tradeCounterparty)) {
            counterpartyScore = 10L;
        } else {
            int editDistance = levenshteinDistance(
                    currCounterparty.toLowerCase(),
                    tradeCounterparty.toLowerCase()
            );

            int maxLength = Math.max(
                    currCounterparty.length(),
                    tradeCounterparty.length()
            );

            double similarity = maxLength == 0
                    ? 1.0
                    : 1.0 - ((double) editDistance / maxLength);

            counterpartyScore = similarity >= 0.8 ? 5L : 0L;
        }

        Long compositeScore = quantityScore + priceScore + settlementDateScore + counterpartyScore;


        TradeMatchCandidate tradeMatchCandidate = new TradeMatchCandidate();
        tradeMatchCandidate.setTradeBreak(tradeBreak);
        tradeMatchCandidate.setCandidateTradeId(trade.getTradeId());
        tradeMatchCandidate.setConfidenceScore(compositeScore);
        tradeMatchCandidate.setMatchTier(computeMatchTier(compositeScore));
        tradeMatchCandidate.setRejected(false);

        ScoreBreakdown breakdown = new ScoreBreakdown(quantityScore, priceScore, settlementDateScore, counterpartyScore);
        tradeMatchCandidate.setScoreBreakdown(breakdown.toString());

        // rank and save handled by caller after top-3 selection
        return tradeMatchCandidate;
    }

    private int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;

            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;

                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }

            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[b.length()];
    }

    private MatchTier computeMatchTier(Long compositeScore) {
        // FIX: thresholds aligned to design (85/60/40)
        return switch (compositeScore) {
            case Long score when score >= 85L -> MatchTier.AUTO_MATCH;
            case Long score when score >= 60L -> MatchTier.PROBABLE_MATCH;
            case Long score when score >= 40L -> MatchTier.POSSIBLE_MATCH;
            default -> MatchTier.NO_MATCH;
        };
    }

    private void rerankCandidates(Long tradeBreakId) {
        List<TradeMatchCandidate> candidates =
                tradeMatchCandidateRepository.findByTradeBreak_IdOrderByConfidenceScoreDesc(tradeBreakId);

        candidates.sort(
                Comparator.comparing(TradeMatchCandidate::getConfidenceScore, Comparator.reverseOrder())
                        .thenComparing(TradeMatchCandidate::getCandidateTradeId)
        );

        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).setRank(i + 1);
        }
        tradeMatchCandidateRepository.saveAll(candidates);
    }

    public void  rejectCandidate(Long candidateId, String rejectedBy){

        TradeMatchCandidate tradeMatchCandidate = tradeMatchCandidateRepository
                .findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Did not find candidate"));

        TradeBreak tradeBreak = tradeMatchCandidate.getTradeBreak();

        tradeMatchCandidate.setRejected(Boolean.TRUE);
        tradeMatchCandidate.setRejectedBy(rejectedBy);
        tradeMatchCandidate.setRejectedAt(java.time.LocalDateTime.now());  // FIX: was missing
        tradeMatchCandidateRepository.save(tradeMatchCandidate);
        rerankCandidates(tradeBreak.getId());

    }

}
