package com.reapro.achat.Controller;

import com.reapro.achat.entities.primary.Exclusion;
import com.reapro.achat.services.ExclusionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search-exclusions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')") // Sécurise tout le controller
public class ExclusionController {

    private final ExclusionService exclusionService;

    @GetMapping
    public List<Exclusion> getAllExclusions() {
        return exclusionService.getAllExclusions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Exclusion addExclusion(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> payload) {
        String normalizedFilter = payload.get("normalizedFilter");
        String reason = payload.get("reason");
        return exclusionService.addExclusion(normalizedFilter, reason, email);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeExclusion(@PathVariable Long id) {
        exclusionService.removeExclusion(id);
    }
}
