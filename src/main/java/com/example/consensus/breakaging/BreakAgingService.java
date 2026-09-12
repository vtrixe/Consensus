package com.example.consensus.breakaging;

import com.example.consensus.model.Enums.BreakStatus;

public interface BreakAgingService {

    void escalateStaleBreaks();

    void pendingConfirmSweep();

    void transitionState(Long breakId, BreakStatus newStatus, String changedBy, String assignedTo, String notes);

}
