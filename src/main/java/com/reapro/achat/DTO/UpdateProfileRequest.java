package com.reapro.achat.DTO;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String firstname;
    private String lastname;
    private String email;
}