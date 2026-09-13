package com.example.consensus.web;

import com.example.consensus.fuzzymatch.FuzzyMatchingService;
import com.example.consensus.model.entity.TradeMatchCandidate;
import com.example.consensus.web.DTOs.Requests.RejectCandidateRequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/candidates/")
@RequiredArgsConstructor
public class TradeMatcher {

    private final FuzzyMatchingService  fuzzyMatchingService;

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectCandidate(@PathVariable Long id, @RequestBody RejectCandidateRequestBody requestBody) {
        fuzzyMatchingService.rejectCandidate(id,requestBody.getRejectedBy());
    }
}
