package com.reapro.achat.partslink;

import java.time.Instant;

public record PartslinkSessionStatusResponse(
        String sessionId,
        PartslinkSessionState state,
        boolean active,
        String viewerUrl,
        Instant lastActivityAt,
        String message
) {
}
