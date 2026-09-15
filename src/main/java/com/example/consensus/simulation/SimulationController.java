package com.example.consensus.simulation;

import com.example.consensus.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final IntraDayFeedSimulator simulator;

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start() {
        String schema = TenantContext.get();
        if (schema == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Api-Key required");
        simulator.start(schema);
    }

    @PostMapping("/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop() {
        String schema = TenantContext.get();
        if (schema == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Api-Key required");
        simulator.stop(schema);
    }
}
