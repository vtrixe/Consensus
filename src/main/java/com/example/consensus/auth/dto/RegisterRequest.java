package com.example.consensus.auth.dto;

import com.example.consensus.auth.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterRequest {
    private String username;
    private Role role;
}
