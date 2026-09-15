package com.example.consensus.web;

import com.example.consensus.fuzzymatch.FuzzyMatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/candidates/")
@RequiredArgsConstructor
public class TradeMatcher {

    private final FuzzyMatchingService  fuzzyMatchingService;

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectCandidate(@PathVariable Long id) {
        String rejectedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        fuzzyMatchingService.rejectCandidate(id, rejectedBy);
    }
}
