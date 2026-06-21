package com.reapro.achat.config.datasource;

import com.reapro.achat.entities.primary.Permission;
import com.reapro.achat.enums.PermissionCode;
import com.reapro.achat.repositories.primary.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Synchronise le registre central {@link PermissionCode} vers la table {@code permissions} au démarrage.
 *
 * <p>Comportement (idempotent) :</p>
 * <ul>
 *   <li>permission absente en base → ajout ;</li>
 *   <li>permission existante → mise à jour de label/description/groupe/type/sensitive/active si modifiés ;</li>
 *   <li>permission existante inchangée → aucune écriture ;</li>
 *   <li>permission retirée du code → <b>laissée inchangée</b> (jamais supprimée brutalement ; une éventuelle
 *       désactivation sera décidée dans un lot ultérieur) ;</li>
 *   <li>les affectations utilisateurs ({@code user_permissions}) ne sont jamais touchées.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionSeeder implements ApplicationRunner {

    private final PermissionRepository permissionRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int created = 0;
            int updated = 0;

            for (PermissionCode pc : PermissionCode.values()) {
                Permission existing = permissionRepository.findByCode(pc.getCode()).orElse(null);

                if (existing == null) {
                    permissionRepository.save(Permission.builder()
                            .code(pc.getCode())
                            .label(pc.getLabel())
                            .description(pc.getDescription())
                            .groupName(pc.getGroup())
                            .type(pc.getType())
                            .sensitive(pc.isSensitive())
                            .active(pc.isActiveByDefault())
                            .build());
                    created++;
                } else if (needsUpdate(existing, pc)) {
                    existing.setLabel(pc.getLabel());
                    existing.setDescription(pc.getDescription());
                    existing.setGroupName(pc.getGroup());
                    existing.setType(pc.getType());
                    existing.setSensitive(pc.isSensitive());
                    existing.setActive(pc.isActiveByDefault());
                    permissionRepository.save(existing);
                    updated++;
                }
            }

            log.info("Permission seed: {} created, {} updated, {} declared in registry.",
                    created, updated, PermissionCode.values().length);

        } catch (Exception e) {
            // Ne jamais bloquer le démarrage de l'application sur une erreur de seed.
            log.error("Failed to seed permissions", e);
        }
    }

    private boolean needsUpdate(Permission existing, PermissionCode pc) {
        return !Objects.equals(existing.getLabel(), pc.getLabel())
                || !Objects.equals(existing.getDescription(), pc.getDescription())
                || !Objects.equals(existing.getGroupName(), pc.getGroup())
                || existing.getType() != pc.getType()
                || existing.isSensitive() != pc.isSensitive()
                || existing.isActive() != pc.isActiveByDefault();
    }
}
