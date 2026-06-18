package com.reapro.achat.services;

import com.reapro.achat.DTO.pausegame.ManufacturerChallengeResponse;
import com.reapro.achat.DTO.pausegame.ManufacturerChoice;
import com.reapro.achat.DTO.pausegame.ManufacturerQuestion;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository.ManufacturerQuizItemProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vérifie les règles du mini-jeu « Défi Fabricant » côté service
 * (sans base ni TecDoc : dépendances mockées). Une instance neuve par test
 * (le service met le pool en cache mémoire).
 */
class PauseGameServiceTest {

    private ElvaItemRepository repo;
    private TecDocService tecDoc;
    private PauseGameService service;

    @BeforeEach
    void setUp() {
        repo = mock(ElvaItemRepository.class);
        tecDoc = mock(TecDocService.class);
        when(tecDoc.getSupplierLogos(any())).thenReturn(Map.of());   // pas de logo par défaut
        service = new PauseGameService(repo, tecDoc);
    }

    /** Implémentation minimale de la projection pour les tests. */
    private static ManufacturerQuizItemProjection proj(String no, String vendorItemNo, String fabricant, String tecdocId) {
        return new ManufacturerQuizItemProjection() {
            public String getNo() { return no; }
            public String getVendorItemNo() { return vendorItemNo; }
            public String getFabricant() { return fabricant; }
            public String getTecdocIdFabricant() { return tecdocId; }
            public String getDescription() { return "Article " + no; }
        };
    }

    /** Pool varié : 8 articles, 6 fabricants distincts. */
    private static List<ManufacturerQuizItemProjection> samplePool() {
        return new ArrayList<>(List.of(
                proj("20388", "0986478521", "BOSCH", "30"),
                proj("20389", "VKBA3656", "VALEO", "31"),
                proj("20390", "6205-2RS", "SKF", "32"),
                proj("20391", "94010", "DAYCO", "33"),
                proj("20392", "BKR6E", "NGK", "34"),
                proj("20393", "01234", "FEBI", "35"),
                proj("20394", "0986478522", "BOSCH", "30"),
                proj("20395", "VKBA3657", "VALEO", "31")
        ));
    }

    @Test
    void eachQuestionHasFourDistinctChoicesWithExactlyOneCorrect() {
        when(repo.findManufacturerQuizCandidates()).thenReturn(samplePool());

        ManufacturerChallengeResponse resp = service.generateManufacturerChallenge(25);

        assertThat(resp.getQuestions()).isNotEmpty();
        for (ManufacturerQuestion q : resp.getQuestions()) {
            assertThat(q.getItemNo()).isNotBlank();
            assertThat(q.getReference()).isNotBlank();
            assertThat(q.getCorrectManufacturerId()).isNotBlank();

            assertThat(q.getChoices()).hasSize(4);

            List<String> ids = q.getChoices().stream().map(ManufacturerChoice::getId).collect(Collectors.toList());
            assertThat(ids).doesNotHaveDuplicates();

            long correct = q.getChoices().stream()
                    .filter(c -> c.getId().equals(q.getCorrectManufacturerId())).count();
            assertThat(correct).isEqualTo(1);

            assertThat(q.getChoices()).allSatisfy(c -> assertThat(c.getName()).isNotBlank());
        }
    }

    @Test
    void referenceUsesVendorItemNo() {
        when(repo.findManufacturerQuizCandidates()).thenReturn(new ArrayList<>(List.of(
                proj("20388", "0986478521", "BOSCH", "30"),
                proj("20389", "VKBA3656", "VALEO", "31"),
                proj("20390", "6205-2RS", "SKF", "32"),
                proj("20391", "94010", "DAYCO", "33"))));

        ManufacturerChallengeResponse resp = service.generateManufacturerChallenge(10);

        // toutes les références affichées correspondent à un Vendor Item No_ du pool
        List<String> vendorRefs = List.of("0986478521", "VKBA3656", "6205-2RS", "94010");
        assertThat(resp.getQuestions()).isNotEmpty();
        assertThat(resp.getQuestions()).allSatisfy(q -> assertThat(vendorRefs).contains(q.getReference()));
    }

    @Test
    void logosAreAppliedWhenAvailable() {
        when(repo.findManufacturerQuizCandidates()).thenReturn(samplePool());
        // BOSCH = supplierId 30 → logo fourni ; les autres → pas de logo (fallback nom)
        when(tecDoc.getSupplierLogos(any())).thenReturn(Map.of(30, "https://logo/bosch.png"));

        ManufacturerChallengeResponse resp = service.generateManufacturerChallenge(25);

        boolean boschHasLogo = resp.getQuestions().stream()
                .flatMap(q -> q.getChoices().stream())
                .filter(c -> "BOSCH".equals(c.getId()))
                .allMatch(c -> "https://logo/bosch.png".equals(c.getLogoUrl()));
        assertThat(boschHasLogo).isTrue();
    }

    @Test
    void noDuplicateItemReferenceAcrossQuestions() {
        when(repo.findManufacturerQuizCandidates()).thenReturn(samplePool());

        ManufacturerChallengeResponse resp = service.generateManufacturerChallenge(40);

        List<String> refs = resp.getQuestions().stream()
                .map(ManufacturerQuestion::getItemNo).collect(Collectors.toList());
        assertThat(refs).doesNotHaveDuplicates();
    }

    @Test
    void requestedCountIsClampedToMax() {
        List<ManufacturerQuizItemProjection> big = new ArrayList<>();
        String[] fabs = {"BOSCH", "VALEO", "SKF", "DAYCO", "NGK", "FEBI"};
        for (int i = 0; i < 200; i++) big.add(proj("ART" + i, "V" + i, fabs[i % fabs.length], "10"));
        when(repo.findManufacturerQuizCandidates()).thenReturn(big);

        ManufacturerChallengeResponse resp = service.generateManufacturerChallenge(999);

        assertThat(resp.getQuestions()).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void returnsEmptyWhenNotEnoughDistinctManufacturers() {
        when(repo.findManufacturerQuizCandidates()).thenReturn(new ArrayList<>(List.of(
                proj("1", "A", "BOSCH", "30"),
                proj("2", "B", "VALEO", "31"),
                proj("3", "C", "BOSCH", "30"))));

        ManufacturerChallengeResponse resp = service.generateManufacturerChallenge(25);

        assertThat(resp.getQuestions()).isEmpty();
    }

    @Test
    void emptyPoolReturnsEmptyQuestions() {
        when(repo.findManufacturerQuizCandidates()).thenReturn(new ArrayList<>());

        ManufacturerChallengeResponse resp = service.generateManufacturerChallenge(25);

        assertThat(resp.getQuestions()).isEmpty();
    }
}
