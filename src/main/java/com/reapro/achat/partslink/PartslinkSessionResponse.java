package com.reapro.achat.partslink;

public record PartslinkSessionResponse(
        String sessionId,
        String viewerUrl,
        PartslinkSessionState state,
        String message
) {
}
