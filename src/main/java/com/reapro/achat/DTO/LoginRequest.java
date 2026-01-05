package com.reapro.achat.DTO;


import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}

