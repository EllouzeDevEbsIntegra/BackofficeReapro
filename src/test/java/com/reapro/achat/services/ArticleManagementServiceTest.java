package com.reapro.achat.services;

import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.articlemanagement.ArticleExtraResponse;
import com.reapro.achat.DTO.articlemanagement.ArticleManagementItemResponse;
import com.reapro.achat.entities.primary.ElvaItemCache;
import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.primary.ArticleCatalogRepository;
import com.reapro.achat.repositories.primary.ArticleCatalogRepository.OemCountProjection;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository.ElvaItemRealTimeProjection;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (perf rework) : la liste est servie par le cache PostgreSQL (mocké),
 * enrichie par le temps réel SQL Server (mocké) + OEM count (cache). Aucune base réelle.
 */
class ArticleManagementServiceTest {

    private ArticleCatalogRepository catalogRepo;
    private ElvaItemRepository elvaItemRepository;
    private LastInvoicedItemCostRepository lastInvoicedRepo;
    private ArticleManagementService service;

    @BeforeEach
    void setUp() {
        catalogRepo = mock(ArticleCatalogRepository.class);
        elvaItemRepository = mock(ElvaItemRepository.class);
        lastInvoicedRepo = mock(LastInvoicedItemCostRepository.class);
        service = new ArticleManagementService(catalogRepo, elvaItemRepository, lastInvoicedRepo);
    }

    // ── Helpers ──
    private ElvaItemCache cache(String no) {
        ElvaItemCache c = new ElvaItemCache();
        c.setNo(no);
        c.setDescription("Filtre " + no);
        c.setDescriptionStructuree("STRUCT " + no);
        c.setItemProductCode("G1");
        c.setGroupe("Groupe 1");
        c.setItemSubProductCode("SG1");
        c.setSousGroupe("Sous 1");
        c.setReferenceOrigineLie("MASTER-" + no);
        c.setCodeFabricant("MFR");
        c.setFabricant("Bosch");
        c.setVendorNo("V100");
        c.setVendorItemNo("VIN-" + no);
        c.setTecdocIdFabricant("100002");
        c.setMakeCode("MK");
        c.setUnitCost(new BigDecimal("3.5"));
        c.setUnitPrice(new BigDecimal("9.0")); // fallback prix si pas de temps réel
        return c;
    }

    private ElvaItemRealTimeProjection rt(String no, String stock, String recep, String price) {
        return new ElvaItemRealTimeProjection() {
            public String getNo() { return no; }
            public BigDecimal getUnitPrice() { return price == null ? null : new BigDecimal(price); }
            public BigDecimal getQuantite() { return stock == null ? null : new BigDecimal(stock); }
            public BigDecimal getReservedQuantity() { return null; }
            public BigDecimal getReceptionQty() { return recep == null ? null : new BigDecimal(recep); }
        };
    }

    private OemCountProjection oem(String refKey, long cnt) {
        return new OemCountProjection() {
            public String getRefKey() { return refKey; }
            public long getCnt() { return cnt; }
        };
    }

    @SuppressWarnings("unchecked")
    private void stubPage(List<ElvaItemCache> items, long total) {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ElvaItemCache> page = new PageImpl<>(items, pageable, total);
        when(catalogRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    }

    // ── Tests ──
    @Test
    void getArticles_mapsCatalogPlusRealtimeAndOem() {
        stubPage(List.of(cache("317542")), 1L);
        when(elvaItemRepository.findRealTimeDataByNos(any()))
                .thenReturn(List.of(rt("317542", "5", "2", "12.300")));
        when(catalogRepo.findOemCountsByKeys(any())).thenReturn(List.of(oem("317542", 7L)));

        PagedResponse<ArticleManagementItemResponse> resp =
                service.getArticles(null, null, null, null, 0, 20);

        assertThat(resp.getTotalElements()).isEqualTo(1L);
        ArticleManagementItemResponse it = resp.getContent().get(0);
        assertThat(it.getItemNo()).isEqualTo("317542");
        assertThat(it.getDescriptionStructured()).isEqualTo("STRUCT 317542");
        assertThat(it.getGroupName()).isEqualTo("Groupe 1");
        assertThat(it.getInventory()).isEqualByComparingTo("5");   // temps réel
        assertThat(it.getQtyImport()).isEqualByComparingTo("2");   // temps réel
        assertThat(it.getUnitPrice()).isEqualByComparingTo("12.300"); // temps réel prioritaire
        assertThat(it.getUnitCost()).isEqualByComparingTo("3.5");  // cache (stable)
        assertThat(it.getOemCount()).isEqualTo(7L);
        assertThat(it.getLastPurchaseDate()).isNull(); // déplacé dans le dialog
        assertThat(it.getTotalSales()).isNull();
    }

    @Test
    void getArticles_emptyPage_noRealtimeNoOem() {
        stubPage(List.of(), 0L);

        PagedResponse<ArticleManagementItemResponse> resp =
                service.getArticles("zzz", null, null, null, 0, 20);

        assertThat(resp.getContent()).isEmpty();
        verify(elvaItemRepository, never()).findRealTimeDataByNos(any());
        verify(catalogRepo, never()).findOemCountsByKeys(any());
    }

    @Test
    void getArticles_nullSafe_whenNoRealtimeData_fallsBackToCachePrice() {
        stubPage(List.of(cache("ABC")), 1L);
        when(elvaItemRepository.findRealTimeDataByNos(any())).thenReturn(List.of()); // pas de temps réel
        when(catalogRepo.findOemCountsByKeys(any())).thenReturn(List.of());

        ArticleManagementItemResponse it =
                service.getArticles(null, null, null, null, 0, 20).getContent().get(0);

        assertThat(it.getInventory()).isNull();       // pas de stock temps réel
        assertThat(it.getQtyImport()).isNull();
        assertThat(it.getUnitPrice()).isEqualByComparingTo("9.0"); // fallback prix cache
        assertThat(it.getOemCount()).isZero();
    }

    @Test
    void getExtra_returnsMostRecentPurchaseDate() {
        LastInvoicedItemCost a = new LastInvoicedItemCost();
        a.setNo("317542"); a.setLastInvoicedCostDate(LocalDate.of(2025, 1, 10));
        LastInvoicedItemCost b = new LastInvoicedItemCost();
        b.setNo("317542"); b.setLastInvoicedCostDate(LocalDate.of(2026, 3, 2));
        when(lastInvoicedRepo.findByNoIn(any())).thenReturn(List.of(a, b));

        ArticleExtraResponse extra = service.getExtra("317542");

        assertThat(extra.getItemNo()).isEqualTo("317542");
        assertThat(extra.getLastPurchaseDate()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void getExtra_nullWhenNoData() {
        when(lastInvoicedRepo.findByNoIn(any())).thenReturn(List.of());
        assertThat(service.getExtra("X").getLastPurchaseDate()).isNull();
    }
}
