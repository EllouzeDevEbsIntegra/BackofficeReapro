package com.backoffice.atelier.DTO;


import lombok.Data;

@Data
public class VerifyCodeRequest {
    private String email;
    private String code;
}

