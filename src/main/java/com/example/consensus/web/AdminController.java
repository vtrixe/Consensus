package com.example.consensus.web;

import com.example.consensus.breakaging.BreakAgingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BreakAgingService breakAgingService;

    @PostMapping("/escalate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void escalate() {
        breakAgingService.escalateStaleBreaks();
    }

    @PostMapping("/pending-confirm-sweep")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pendingConfirmSweep() {
        breakAgingService.pendingConfirmSweep();
    }
}
