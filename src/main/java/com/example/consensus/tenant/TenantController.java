package com.example.consensus.tenant;

import com.example.consensus.tenant.dto.TenantRegisterRequest;
import com.example.consensus.tenant.dto.TenantRegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "Register tenants and retrieve API keys")
public class TenantController {

    private final TenantRegistrationService tenantRegistrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(
        summary = "Register a new tenant",
        description = "Creates an isolated PostgreSQL schema for the tenant and returns the API key required for all subsequent requests."
    )
    @ApiResponse(responseCode = "201", description = "Tenant created — save the apiKey, it is not retrievable again")
    @ApiResponse(responseCode = "409", description = "Tenant name already exists")
    public TenantRegisterResponse register(@RequestBody TenantRegisterRequest request) {
        Tenant tenant = tenantRegistrationService.register(request.getName());
        return new TenantRegisterResponse(tenant.getName(), tenant.getSchemaName(), tenant.getApiKey());
    }
}
