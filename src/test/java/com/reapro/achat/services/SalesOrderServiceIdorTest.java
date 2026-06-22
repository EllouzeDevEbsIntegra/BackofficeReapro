package com.reapro.achat.services;

import com.reapro.achat.DTO.salesorder.SalesOrderResponse;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.primary.SalesOrder;
import com.reapro.achat.enums.OrderStatus;
import com.reapro.achat.enums.Role;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.repositories.primary.SalesOrderLineRepository;
import com.reapro.achat.repositories.primary.SalesOrderRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import com.reapro.achat.services.bc.BusinessCentralSalesOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot 4bis-A — IDOR sur {@code GET /api/orders/{orderId}} :
 * seul le propriétaire (ou un super-admin) peut lire une commande ; un autre utilisateur reçoit
 * ORDER_NOT_FOUND (ne révèle pas l'existence).
 */
class SalesOrderServiceIdorTest {

    private SalesOrderRepository salesOrderRepository;
    private AdminRepository adminRepository;
    private SalesOrderService service;

    @BeforeEach
    void setUp() {
        salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrderLineRepository lineRepository = mock(SalesOrderLineRepository.class);
        adminRepository = mock(AdminRepository.class);
        BusinessCentralSalesOrderService bcService = mock(BusinessCentralSalesOrderService.class);
        ElvaItemRepository elvaItemRepository = mock(ElvaItemRepository.class);
        service = new SalesOrderService(salesOrderRepository, lineRepository, adminRepository, bcService, elvaItemRepository);
    }

    private Admin admin(String email, Role role) {
        return Admin.builder().id(1L).email(email).firstname("F").lastname("L").role(role).active(true).build();
    }

    private SalesOrder orderOwnedBy(Admin owner) {
        // Statut DRAFT → pas de synchronisation BC déclenchée dans getOrderById.
        return SalesOrder.builder().id(1L).clientId("C1").status(OrderStatus.DRAFT).createdBy(owner).build();
    }

    @Test
    void owner_canReadOrder() {
        Admin owner = admin("owner@x.com", Role.ROLE_USER);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(orderOwnedBy(owner)));
        when(adminRepository.findByEmail("owner@x.com")).thenReturn(Optional.of(owner));

        SalesOrderResponse resp = service.getOrderById("owner@x.com", 1L);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(1L);
    }

    @Test
    void otherUser_cannotReadOrder() {
        Admin owner = admin("owner@x.com", Role.ROLE_USER);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(orderOwnedBy(owner)));
        when(adminRepository.findByEmail("other@x.com")).thenReturn(Optional.of(admin("other@x.com", Role.ROLE_USER)));

        ApiException ex = catchThrowableOfType(() -> service.getOrderById("other@x.com", 1L), ApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void superAdmin_canReadAnyOrder() {
        Admin owner = admin("owner@x.com", Role.ROLE_USER);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(orderOwnedBy(owner)));
        when(adminRepository.findByEmail("admin@x.com")).thenReturn(Optional.of(admin("admin@x.com", Role.ROLE_ADMIN)));

        SalesOrderResponse resp = service.getOrderById("admin@x.com", 1L);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(1L);
    }
}
