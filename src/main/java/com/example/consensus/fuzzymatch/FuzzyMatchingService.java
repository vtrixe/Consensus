package com.example.consensus.fuzzymatch;

public interface FuzzyMatchingService {

    void  matchBreak(Long breakId);

    void rejectCandidate(Long candidateId, String rejectedBy);
}
