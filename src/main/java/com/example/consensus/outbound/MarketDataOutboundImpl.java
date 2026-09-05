package com.example.consensus.outbound;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Component
public class MarketDataOutboundImpl implements MarketDataOutbound {

    private final RestClient restClient;
    private final String apiKey;

    public MarketDataOutboundImpl(
            RestClient.Builder builder,
            @Value("${alphavantage.base.url}") String baseUrl,
            @Value("${alphavantage.api.key}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public EquityPrices getDailySeries(String symbol) {
        EquityPrices response = restClient.get()
                .uri("/query?function=TIME_SERIES_DAILY&symbol={symbol}&apikey={apiKey}",
                        symbol, apiKey)
                .retrieve()
                .body(EquityPrices.class);

        if (Objects.isNull(response) || Objects.isNull(response.timeSeriesDaily())) {
            throw new IllegalStateException("No data returned from Alpha Vantage for symbol: " + symbol);
        }

        return response;
    }
}