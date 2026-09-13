package com.example.consensus.web.DTOs.Requests;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RejectCandidateRequestBody {
    String rejectedBy;
}
