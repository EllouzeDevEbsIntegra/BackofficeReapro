package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OemCountDetailsResponseDTO {
    private int totalCount;
    private List<OemCountDetailDTO> details;
}
