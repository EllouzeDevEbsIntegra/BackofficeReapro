package com.reapro.achat.services;

import com.reapro.achat.DTO.PurchaseLineSqlServerDTO;
import com.reapro.achat.entities.sqlserver.PurchaseLineSqlServer;
import com.reapro.achat.repositories.sqlserver.PurchaseLineSqlServerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PurchaseLineSqlServerService {

    private final PurchaseLineSqlServerRepository purchaseLineSqlServerRepository;

    public PurchaseLineSqlServerService(PurchaseLineSqlServerRepository purchaseLineSqlServerRepository) {
        this.purchaseLineSqlServerRepository = purchaseLineSqlServerRepository;
    }

    public Page<PurchaseLineSqlServerDTO> getPurchaseLinesByNo(String no, Pageable pageable) {
        Page<PurchaseLineSqlServer> purchaseLinesPage = purchaseLineSqlServerRepository.findByNo(no, pageable);
        return purchaseLinesPage.map(this::convertToDto);
    }

    private PurchaseLineSqlServerDTO convertToDto(PurchaseLineSqlServer entity) {
        PurchaseLineSqlServerDTO dto = new PurchaseLineSqlServerDTO();
        dto.setDocumentNo(entity.getDocumentNo());
        dto.setBuyFromVendorNo(entity.getBuyFromVendorNo());
        dto.setNo(entity.getNo());
        dto.setLocationCode(entity.getLocationCode());
        dto.setOrderDate(entity.getOrderDate());
        dto.setDescription(entity.getDescription());
        dto.setQuantity(entity.getQuantity());
        dto.setOutstandingQuantity(entity.getOutstandingQuantity());
        dto.setQtyFirstConfirmation(entity.getQtyFirstConfirmation());
        return dto;
    }
}
