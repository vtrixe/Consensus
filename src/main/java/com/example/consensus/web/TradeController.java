package com.example.consensus.web;

import com.example.consensus.loader.TradeGenerator;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeGenerator tradeGenerator;
    private final TradeRepository tradeRepository;

    // AV-backed synthetic generation — fetches real prices and seeds trades
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void generate() {
        tradeGenerator.generate();
    }

    // User-supplied trades — accepts any list of blotter/custodian trades as JSON
    // Duplicate (tradeId + source) pairs are skipped (ON CONFLICT DO NOTHING via unique constraint)
    @PostMapping("/ingest")
    public List<Trade> ingest(@RequestBody List<Trade> trades) {
        return tradeRepository.saveAll(trades);
    }
}
