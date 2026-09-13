package com.example.consensus.web.DTOs.Requests;

import com.example.consensus.model.Enums.BreakStatus;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class UpdateBreakRequestBody {
    private BreakStatus newStatus;
    private String changedBy;
    private String assignedTo;
    private String notes;
}
