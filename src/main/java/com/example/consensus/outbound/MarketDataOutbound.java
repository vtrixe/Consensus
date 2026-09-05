package com.example.consensus.outbound;
import com.example.consensus.outbound.EquityPrices;

public interface MarketDataOutbound {

    public EquityPrices getDailySeries(String symbol);
}