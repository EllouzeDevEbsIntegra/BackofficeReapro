package com.reapro.achat.enums;

/**
 * Registre central <b>versionné</b> des permissions RBAC Reapro (Lot 1 — base backend).
 *
 * <p>Source de vérité côté code : le {@code PermissionSeeder} synchronise automatiquement ces entrées
 * vers la table {@code permissions} au démarrage (ajout / mise à jour, jamais de suppression brutale).
 * Le frontend ne fait que du masquage ergonomique ; le backend reste la source de vérité.</p>
 *
 * <p><b>Règle</b> : toute nouvelle fonctionnalité doit être rattachée à une permission existante,
 * ou déclarer ici une nouvelle permission de niveau module/fonctionnalité (cf.
 * Reapro-RBAC-permissions-roadmap.md). Le {@code code} en base = {@link #name()}.</p>
 *
 * <p><b>Permissions futures</b> (documentées, volontairement <i>non</i> déclarées pour ne pas
 * être semées prématurément ; à activer quand la fonctionnalité existera) :
 * {@code ARTICLE_WRITE}, {@code BC_WRITE}, {@code PARTSLINK_EXPORT}, {@code SEARCH_OPPORTUNITIES_ACTIONS}.
 * ({@code ARTICLE_PHOTO_MANAGE} a été déclarée au Lot 4bis-B pour protéger l'upload/suppression de photos.)</p>
 */
public enum PermissionCode {

    // ── Comparateur ──────────────────────────────────────────────────────────
    COMPARATOR_ACCESS("Accès Comparateur", "Accès au module Comparateur (FRS / EQV / KIT)",
            "Comparateur", PermissionType.MODULE, false, true),
    COMPARATOR_CART_ACTIONS("Actions panier Comparateur", "Constitution / modification du panier achat dans le Comparateur",
            "Comparateur", PermissionType.ACTION, false, true),

    // ── Confirmation Achat ───────────────────────────────────────────────────
    PURCHASE_CONFIRMATION_ACCESS("Accès Confirmation Achat", "Accès au module Confirmation Achat",
            "Confirmation Achat", PermissionType.MODULE, false, true),
    PURCHASE_CONFIRMATION_ACTIONS("Validation Confirmation Achat", "Validation / actions de décision dans Confirmation Achat",
            "Confirmation Achat", PermissionType.ACTION, false, true),

    // ── B2B ──────────────────────────────────────────────────────────────────
    B2B_ACCESS("Accès B2B", "Accès au module B2B interne",
            "B2B", PermissionType.MODULE, false, true),

    // ── Analyse B2B / Search Opportunities ───────────────────────────────────
    SEARCH_OPPORTUNITIES_ACCESS("Accès Analyse B2B", "Accès à l'analyse B2B / Search Opportunities",
            "Analyse B2B", PermissionType.MODULE, false, true),

    // ── Sync Adaptable ───────────────────────────────────────────────────────
    ADAPTABLE_SYNC_ACCESS("Accès Sync Adaptable", "Accès au module Sync Adaptable",
            "Sync Adaptable", PermissionType.MODULE, false, true),
    ADAPTABLE_SYNC_RUN("Déclencher la synchronisation", "Déclenchement de la synchronisation Sync Adaptable",
            "Sync Adaptable", PermissionType.ACTION, false, true),

    // ── Partslink ────────────────────────────────────────────────────────────
    PARTSLINK_ACCESS("Accès Partslink", "Accès au module Partslink",
            "Partslink", PermissionType.MODULE, false, true),

    // ── Articles / TecDoc ────────────────────────────────────────────────────
    TECDOC_CATALOG_ACCESS("Accès Catalogue TecDoc", "Accès à la page Catalogue TecDoc complète",
            "Articles / TecDoc", PermissionType.MODULE, false, true),
    ARTICLE_INFO_READ("Info Article", "Accès au dialog Info Article (transverse : B2B, Comparateur, Confirmation Achat, Gestion Articles)",
            "Articles / TecDoc", PermissionType.ACTION, false, true),
    ARTICLE_MANAGEMENT_ACCESS("Accès Gestion Articles", "Accès à la page Gestion Articles",
            "Articles / TecDoc", PermissionType.MODULE, false, true),
    ARTICLE_PHOTO_MANAGE("Gestion photos article", "Upload / suppression des photos article (écriture BC)",
            "Articles / TecDoc", PermissionType.ACTION, false, true),

    // ── Administration (permissions sensibles — anti-escalade, cf. roadmap §7) ─
    SETTINGS_ACCESS("Ouvrir Paramètres", "Ouverture de la section Paramètres",
            "Administration", PermissionType.ADMIN, true, true),
    USER_MANAGEMENT_ACCESS("Gestion Utilisateurs", "Onglet Utilisateurs (créer / bloquer / modifier les comptes)",
            "Administration", PermissionType.ADMIN, true, true),
    PERMISSION_ASSIGNMENT_ACCESS("Gestion Autorisations", "Onglet Autorisations (affectation des permissions métier)",
            "Administration", PermissionType.ADMIN, true, true),
    SYSTEM_SETTINGS_ACCESS("Paramètres système", "Onglet Paramètres système",
            "Administration", PermissionType.ADMIN, true, true);

    private final String label;
    private final String description;
    private final String group;
    private final PermissionType type;
    private final boolean sensitive;
    private final boolean activeByDefault;

    PermissionCode(String label, String description, String group,
                   PermissionType type, boolean sensitive, boolean activeByDefault) {
        this.label = label;
        this.description = description;
        this.group = group;
        this.type = type;
        this.sensitive = sensitive;
        this.activeByDefault = activeByDefault;
    }

    /** Code persisté en base ({@code permissions.code}). */
    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getGroup() {
        return group;
    }

    public PermissionType getType() {
        return type;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public boolean isActiveByDefault() {
        return activeByDefault;
    }
}
