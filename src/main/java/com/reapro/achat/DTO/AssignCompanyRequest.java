package com.reapro.achat.DTO;

/**
 * Affectation de la société BC d'un utilisateur par un SUPER ADMIN (RBAC).
 * Action sensible : réservée à {@code superAdmin} (voir UserAdminService.setUserCompany).
 */
public record AssignCompanyRequest(String bcCompanyId) {}
