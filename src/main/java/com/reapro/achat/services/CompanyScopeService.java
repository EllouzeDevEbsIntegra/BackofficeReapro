package com.reapro.achat.services;

import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.repositories.primary.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Résolution de la société Business Central dans le contexte de l'utilisateur authentifié (Lot 4bis-A).
 *
 * <p><b>Sécurité (anti company-spoofing) :</b> les endpoints BC strictement « société de l'utilisateur »
 * ne doivent JAMAIS faire confiance à un {@code companyId} fourni par le client (qui permettait d'accéder
 * à une autre société). Ils résolvent désormais la société depuis {@link Admin#getBcCompanyId()}.</p>
 *
 * <p>Comportement uniforme (y compris super-admin — pas de bypass global injustifié, cf. roadmap §règle 5) :
 * la société utilisée est toujours celle affectée au compte. Si aucune société n'est affectée et que
 * l'endpoint en a besoin → <b>403</b> (erreur contrôlée).</p>
 */
@Service
@RequiredArgsConstructor
public class CompanyScopeService {

    private final AdminRepository adminRepository;

    /** Renvoie la société BC affectée à l'utilisateur, ou lève 403 si aucune (401 si compte introuvable). */
    @Transactional(readOnly = true)
    public String requireUserCompanyId(String email) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide."));
        String companyId = admin.getBcCompanyId();
        if (companyId == null || companyId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Aucune société n'est affectée à votre compte. Contactez un administrateur.");
        }
        return companyId;
    }
}
