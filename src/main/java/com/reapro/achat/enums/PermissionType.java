package com.reapro.achat.enums;

/**
 * Type/portée d'une permission du registre RBAC Reapro (Lot 1).
 *
 * <ul>
 *   <li>{@code MODULE} : accès à un module / une page (ex. Comparateur, B2B).</li>
 *   <li>{@code ACTION} : action fonctionnelle au sein d'un module (ex. actions panier).</li>
 *   <li>{@code ADMIN}  : permission d'administration (Paramètres, gestion utilisateurs/autorisations).</li>
 * </ul>
 */
public enum PermissionType {
    MODULE,
    ACTION,
    ADMIN
}
