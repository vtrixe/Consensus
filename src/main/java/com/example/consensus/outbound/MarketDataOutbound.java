package com.example.consensus.outbound;

public interface MarketDataOutbound {

    public EquityPrices getDailySeries(String symbol);
}