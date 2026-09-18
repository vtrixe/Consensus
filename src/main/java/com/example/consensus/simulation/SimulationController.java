package com.example.consensus.simulation;

import com.example.consensus.tenant.TenantContext;
import com.example.consensus.web.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/simulation")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "Control the intra-day trade feed simulator per tenant")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "ApiKeyAuth")
public class SimulationController {

    private final IntraDayFeedSimulator simulator;

    @PostMapping("/start")
    @Operation(summary = "Start the trade feed simulator", description = "Begins emitting synthetic trades for the caller's tenant schema, firing Kafka events and triggering reconciliation.")
    @ApiResponse(responseCode = "200", description = "Simulator started")
    @ApiResponse(responseCode = "401", description = "Missing or invalid X-Api-Key")
    public SuccessResponse start() {
        String schema = TenantContext.get();
        if (schema == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Api-Key required");
        simulator.start(schema);
        return SuccessResponse.of("Simulator started for " + schema);
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop the trade feed simulator", description = "Halts trade emission for the caller's tenant.")
    @ApiResponse(responseCode = "200", description = "Simulator stopped")
    public SuccessResponse stop() {
        String schema = TenantContext.get();
        if (schema == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Api-Key required");
        simulator.stop(schema);
        return SuccessResponse.of("Simulator stopped for " + schema);
    }
}
