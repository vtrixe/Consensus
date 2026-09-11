package com.example.consensus.outbound;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Slf4j
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

        // Temporary diagnostic — remove once seeding works
        String rawCheck = restClient.get()
                .uri("/query?function=TIME_SERIES_DAILY&symbol={symbol}&apikey={apiKey}", symbol, apiKey)
                .retrieve()
                .body(String.class);
        log.info("RAW AV response for {}: {}", symbol, rawCheck == null ? "null" : rawCheck.substring(0, Math.min(300, rawCheck.length())));

        if (Objects.isNull(response) || Objects.isNull(response.getTimeSeriesDaily())) {
            throw new IllegalStateException("No data returned from Alpha Vantage for symbol: " + symbol);
        }

        return response;
    }
}