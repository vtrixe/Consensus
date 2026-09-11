package com.example.consensus.reconciliation;

import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.Enums.MaterialityTier;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MaterialityScorer {

    // ── Settlement cutoff constants (DTC / US Equities) ───────────────────────
    private static final LocalTime DTC_CUTOFF_TIME  = LocalTime.of(15, 0);
    private static final ZoneId    SETTLEMENT_ZONE   = ZoneId.of("America/New_York");

    // ── Notional thresholds ───────────────────────────────────────────────────
    private static final BigDecimal TEN_THOUSAND      = BigDecimal.valueOf(10_000);
    private static final BigDecimal ONE_HUNDRED_K     = BigDecimal.valueOf(100_000);
    private static final BigDecimal ONE_BPS           = BigDecimal.valueOf(0.0001);

    // ── Counterparty tiers (1 = bulge bracket, 2 = major prop/MM, 3 = others)
    private static final Map<String, Integer> COUNTERPARTY_TIERS = new HashMap<>();
    static {
        COUNTERPARTY_TIERS.put("Goldman Sachs",   1);
        COUNTERPARTY_TIERS.put("JPMorgan",        1);
        COUNTERPARTY_TIERS.put("Morgan Stanley",  1);
        COUNTERPARTY_TIERS.put("UBS",             1);
        COUNTERPARTY_TIERS.put("Citadel Securities", 2);
        COUNTERPARTY_TIERS.put("Virtu Financial", 2);
        COUNTERPARTY_TIERS.put("Jane Street",     2);
        COUNTERPARTY_TIERS.put("Two Sigma",       3);  // tier 3 — less automated ops
    }

    // ── Average daily volume per symbol (shares) ──────────────────────────────
    private static final Map<String, Long> SYMBOL_ADV = Map.of(
            "AAPL", 60_000_000L,
            "MSFT", 25_000_000L,
            "JPM",  10_000_000L
    );

    // ── Base resolution time per break type (minutes) ─────────────────────────
    private static final Map<BreakType, Long> BASE_RESOLUTION_MINUTES = Map.of(
            BreakType.MISSING_CUSTODIAN,         180L,
            BreakType.MISSING_BLOTTER,           120L,
            BreakType.PRICE_MISMATCH,             45L,
            BreakType.QUANTITY_MISMATCH,          60L,
            BreakType.SETTLEMENT_DATE_MISMATCH,   20L,
            BreakType.COUNTERPARTY_MISMATCH,     180L
    );

    private final TradeRepository tradeRepository;

    // ── PUBLIC: Step 1 — compute notional impact + old materiality tier ───────
    // Also sets settlementDate on tradeBreak so urgency scorer can use it
    public void notion(TradeBreak tradeBreak) {
        // Fetch the primary trade (blotter for most types)
        Trade primary = fetch(tradeBreak.getTradeId(), primarySource(tradeBreak.getBreakType()));

        // Set settlementDate as the DTC cutoff moment: 15:00 ET on the trade's settlement date
        tradeBreak.setSettlementDate(
                primary.getSettlementDate()
                        .atTime(DTC_CUTOFF_TIME)
                        .atZone(SETTLEMENT_ZONE)
                        .toLocalDateTime()
        );

        BigDecimal notional = switch (tradeBreak.getBreakType()) {

            case MISSING_CUSTODIAN, MISSING_BLOTTER ->
                    primary.getPrice().multiply(primary.getQuantity());

            case PRICE_MISMATCH -> {
                Trade cust = fetch(tradeBreak.getTradeId(), DataSource.CUSTODIAN);
                yield primary.getQuantity()
                        .multiply(primary.getPrice().subtract(cust.getPrice()).abs());
            }

            case QUANTITY_MISMATCH -> {
                Trade cust = fetch(tradeBreak.getTradeId(), DataSource.CUSTODIAN);
                yield primary.getPrice()
                        .multiply(primary.getQuantity().subtract(cust.getQuantity()).abs());
            }

            case SETTLEMENT_DATE_MISMATCH -> {
                Trade cust = fetch(tradeBreak.getTradeId(), DataSource.CUSTODIAN);
                long days = Math.abs(ChronoUnit.DAYS.between(
                        primary.getSettlementDate(), cust.getSettlementDate()));
                yield primary.getPrice().multiply(primary.getQuantity())
                        .multiply(ONE_BPS).multiply(BigDecimal.valueOf(days));
            }

            case COUNTERPARTY_MISMATCH -> BigDecimal.ZERO;
        };

        tradeBreak.setNotionalImpact(notional);

        // Old threshold-based tier (kept separate from composite score per design)
        MaterialityTier tier = tradeBreak.getBreakType() == BreakType.COUNTERPARTY_MISMATCH
                ? MaterialityTier.MAJOR
                : notionalTier(notional);
        tradeBreak.setMaterialityTier(tier);
    }

    // ── PUBLIC: Step 2 — composite weighted score (0–100) ────────────────────
    // Requires notion() to have been called first (needs notionalImpact + settlementDate set)
    public long scoreTradeBreak(TradeBreak tradeBreak) {
        Trade primary = fetch(tradeBreak.getTradeId(), primarySource(tradeBreak.getBreakType()));

        long urgency    = settlementUrgencyScore(tradeBreak);   // sets minutesToSettlement
        long adv        = advConcentrationScore(primary.getSymbol(), primary.getQuantity());
        long notional   = notionalImpactScore(tradeBreak.getNotionalImpact());
        long cpTier     = counterpartyTierScore(primary.getCounterparty());

        return urgency + adv + notional + cpTier;
    }

    // ── PUBLIC: resolution time estimate for feasibility gate ────────────────
    public long estimateResolutionMinutes(BreakType breakType, String counterparty,
                                          String symbol, BigDecimal quantity) {
        long base = BASE_RESOLUTION_MINUTES.getOrDefault(breakType, 60L);

        int tier = COUNTERPARTY_TIERS.getOrDefault(counterparty, 3);
        double tierMultiplier = switch (tier) {
            case 1  -> 0.8;   // established relationship, likely automated
            case 2  -> 1.0;   // baseline
            default -> 1.3;   // smaller / unknown — slower
        };

        double advMultiplier = 1.0;
        Long adv = SYMBOL_ADV.get(symbol);
        if (adv != null && adv > 0L && quantity != null) {
            BigDecimal concentration = quantity
                    .divide(BigDecimal.valueOf(adv), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (concentration.compareTo(BigDecimal.valueOf(20)) >= 0)     advMultiplier = 1.4;
            else if (concentration.compareTo(BigDecimal.valueOf(5)) >= 0) advMultiplier = 1.15;
        }

        return Math.round(base * tierMultiplier * advMultiplier);
    }

    // ── Private scoring components ────────────────────────────────────────────

    // Bug fix 1+2: use trade's actual settlement date, correct ZonedDateTime arithmetic
    private long settlementUrgencyScore(TradeBreak tradeBreak) {
        // settlementDate is already stored as 15:00 ET on the settlement date
        ZonedDateTime cutoff = tradeBreak.getSettlementDate()
                .atZone(SETTLEMENT_ZONE);

        long minutes = ChronoUnit.MINUTES.between(
                ZonedDateTime.now(SETTLEMENT_ZONE), cutoff);

        tradeBreak.setMinutesToSettlement(minutes);  // stored for feasibility gate in caller

        if (minutes < 0)    return 40L;  // past cutoff — settlement already failed
        if (minutes < 60)   return 30L;  // < 1 hour — critical window
        if (minutes < 240)  return 20L;  // 1–4 hours — urgent
        if (minutes < 480)  return 10L;  // 4–8 hours — same day, monitor
        return 0L;                        // > 8 hours or future date — low urgency
    }

    private long advConcentrationScore(String symbol, BigDecimal quantity) {
        Long adv = SYMBOL_ADV.get(symbol);
        if (adv == null || adv == 0L) return 0L;

        BigDecimal concentration = quantity
                .divide(BigDecimal.valueOf(adv), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (concentration.compareTo(BigDecimal.valueOf(50)) >= 0) return 30L;
        if (concentration.compareTo(BigDecimal.valueOf(20)) >= 0) return 22L;
        if (concentration.compareTo(BigDecimal.valueOf(5))  >= 0) return 15L;
        if (concentration.compareTo(BigDecimal.valueOf(1))  >= 0) return 8L;
        return 0L;
    }

    private long notionalImpactScore(BigDecimal notional) {
        if (notional == null) return 0L;
        if (notional.compareTo(BigDecimal.valueOf(50_000_000)) >= 0) return 20L;
        if (notional.compareTo(BigDecimal.valueOf(500_000))    >= 0) return 15L;  // ISDA dispute threshold
        if (notional.compareTo(BigDecimal.valueOf(100_000))    >= 0) return 10L;
        if (notional.compareTo(BigDecimal.valueOf(10_000))     >= 0) return 5L;
        return 0L;
    }

    private long counterpartyTierScore(String counterparty) {
        int tier = COUNTERPARTY_TIERS.getOrDefault(counterparty, 3);
        return switch (tier) {
            case 1  -> 0L;   // bulge bracket — fastest resolution
            case 2  -> 5L;   // major prop/MM
            default -> 10L;  // tier 3 / unknown
        };
    }

    private MaterialityTier notionalTier(BigDecimal notional) {
        if (notional.compareTo(TEN_THOUSAND) < 0)   return MaterialityTier.MINOR;
        if (notional.compareTo(ONE_HUNDRED_K) <= 0) return MaterialityTier.MAJOR;
        return MaterialityTier.CRITICAL;
    }

    // Bug fix 1: MISSING_CUSTODIAN → fetch BLOTTER (custodian doesn't exist)
    //            MISSING_BLOTTER   → fetch CUSTODIAN (blotter doesn't exist)
    private DataSource primarySource(BreakType breakType) {
        return switch (breakType) {
            case MISSING_CUSTODIAN -> DataSource.BLOTTER;
            case MISSING_BLOTTER   -> DataSource.CUSTODIAN;
            default                -> DataSource.BLOTTER;
        };
    }

    private Trade fetch(String tradeId, DataSource source) {
        return tradeRepository.findByTradeIdAndSource(tradeId, source)
                .orElseThrow(() -> new IllegalStateException(
                        "Trade not found: " + tradeId + " / " + source));
    }
}
