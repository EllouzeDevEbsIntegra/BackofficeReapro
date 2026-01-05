package com.reapro.achat.DTO;

import com.reapro.achat.enums.Role;
import lombok.Data;

@Data
public class UpdateAdminRequest {
    private String firstname;
    private String lastname;
    private String email;
    private Role role;
}