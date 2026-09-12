package com.example.consensus.breakaging;

import com.example.consensus.model.Enums.BreakStatus;

public interface BreakAgingService {

    public void escalateStaleBreaks();

    public void transitionState(Long breakId, BreakStatus newStatus, String assignedTo, String notes);

}
