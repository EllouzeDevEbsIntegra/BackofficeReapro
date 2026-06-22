package com.reapro.achat.DTO;

import java.util.List;

/** Liste de codes de permissions pour l'attribution / le retrait en lot (RBAC Lot 2). */
public record PermissionCodesRequest(List<String> codes) {}
