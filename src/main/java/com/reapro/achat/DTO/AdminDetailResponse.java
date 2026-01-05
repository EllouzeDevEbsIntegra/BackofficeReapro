package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AdminDetailResponse {
    private Long id;
    private String firstname;
    private String lastname;
    private String email;
    private boolean active;
    private String role;
    private String bcCompanyId;
    private String bcCompanyName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}