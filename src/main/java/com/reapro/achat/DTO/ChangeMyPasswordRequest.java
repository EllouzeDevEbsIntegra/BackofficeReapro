package com.reapro.achat.DTO;

import lombok.Data;

@Data
public class ChangeMyPasswordRequest {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}