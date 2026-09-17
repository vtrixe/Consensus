package com.example.consensus.auth;

import com.example.consensus.auth.dto.AuthResponse;
import com.example.consensus.auth.dto.LoginRequest;
import com.example.consensus.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register users and obtain JWT tokens")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "ApiKeyAuth")
    @Operation(
        summary = "Register a user",
        description = "Creates a user in the caller's tenant schema. Requires X-Api-Key to scope the user to the correct tenant."
    )
    @ApiResponse(responseCode = "201", description = "User registered")
    public void register(@RequestBody RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setRole(request.getRole() != null ? request.getRole() : Role.ANALYST);
        userRepository.save(user);
        log.info("Registered user={}, role={}", user.getUsername(), user.getRole());
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
        summary = "Login and obtain JWT",
        description = "Returns a signed JWT. Pass this as `Authorization: Bearer <token>` on subsequent requests. Requires X-Api-Key to scope the lookup to the correct tenant."
    )
    @ApiResponse(responseCode = "200", description = "JWT token returned")
    @ApiResponse(responseCode = "401", description = "User not found in this tenant")
    public AuthResponse login(@RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return new AuthResponse(jwtUtil.generate(user.getUsername(), user.getRole()));
    }
}
