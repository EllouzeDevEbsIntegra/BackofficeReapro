package com.reapro.achat.config.datasource;

import com.reapro.achat.entities.primary.Permission;
import com.reapro.achat.enums.PermissionCode;
import com.reapro.achat.repositories.primary.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires (sans DB) du seeder de permissions — RBAC Lot 1.
 * Vérifie l'idempotence : ajout si absent, mise à jour si modifié, aucune écriture si à jour,
 * jamais de suppression.
 */
class PermissionSeederTest {

    private PermissionRepository repository;
    private PermissionSeeder seeder;

    @BeforeEach
    void setUp() {
        repository = mock(PermissionRepository.class);
        seeder = new PermissionSeeder(repository);
    }

    private Permission fromRegistry(PermissionCode pc) {
        return Permission.builder()
                .id(1L)
                .code(pc.getCode())
                .label(pc.getLabel())
                .description(pc.getDescription())
                .groupName(pc.getGroup())
                .type(pc.getType())
                .sensitive(pc.isSensitive())
                .active(pc.isActiveByDefault())
                .build();
    }

    @Test
    void seedsAllWhenEmpty_andNeverDeletes() {
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());

        seeder.run(null);

        verify(repository, times(PermissionCode.values().length)).save(any(Permission.class));
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
        verify(repository, never()).deleteAll();
    }

    @Test
    void idempotent_whenRegistryAlreadyUpToDate_noWrite() {
        when(repository.findByCode(anyString())).thenAnswer(inv -> {
            PermissionCode pc = PermissionCode.valueOf(inv.getArgument(0));
            return Optional.of(fromRegistry(pc));
        });

        seeder.run(null);

        verify(repository, never()).save(any(Permission.class));
    }

    @Test
    void updatesExisting_whenMetadataChanged() {
        // Toutes les permissions existent mais avec un label périmé → chaque entrée est mise à jour.
        when(repository.findByCode(anyString())).thenAnswer(inv -> {
            PermissionCode pc = PermissionCode.valueOf(inv.getArgument(0));
            Permission stale = fromRegistry(pc);
            stale.setLabel("ANCIEN LABEL");
            return Optional.of(stale);
        });

        seeder.run(null);

        verify(repository, times(PermissionCode.values().length)).save(any(Permission.class));
        verify(repository, never()).delete(any());
    }
}
