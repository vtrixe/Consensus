package com.example.consensus.tenant;

import com.example.consensus.tenant.dto.TenantRegisterRequest;
import com.example.consensus.tenant.dto.TenantRegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRegistrationService tenantRegistrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantRegisterResponse register(@RequestBody TenantRegisterRequest request) {
        Tenant tenant = tenantRegistrationService.register(request.getName());
        return new TenantRegisterResponse(tenant.getName(), tenant.getSchemaName(), tenant.getApiKey());
    }
}
