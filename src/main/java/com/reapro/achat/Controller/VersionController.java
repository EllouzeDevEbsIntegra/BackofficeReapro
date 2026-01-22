package com.reapro.achat.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/version")
@RequiredArgsConstructor
public class VersionController {

    private final BuildProperties buildProperties;
    private final Optional<GitProperties> gitProperties;

    @GetMapping
    public Map<String, String> getVersion() {
        Map<String, String> versionInfo = new HashMap<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault());

        String buildTime;
        try {
            Instant time = buildProperties.getTime();
            if (time != null) {
                buildTime = formatter.format(time);
            } else {
                buildTime = "Date inconnue";
            }
        } catch (Exception e) {
            buildTime = "Erreur date";
        }
        
        String version = buildProperties.getVersion() != null ? buildProperties.getVersion() : "0.0.0";
        String cleanVersion = version.replace("-SNAPSHOT", "");
        
        // Format final : "V1.0.4 - 22/01/2026 16:23"
        String displayVersion = String.format("V%s - %s", cleanVersion, buildTime);

        versionInfo.put("displayVersion", displayVersion);
        
        // Infos techniques
        versionInfo.put("rawVersion", version);
        versionInfo.put("commitId", gitProperties.map(GitProperties::getShortCommitId).orElse("dev"));
        versionInfo.put("buildTime", buildTime);
        versionInfo.put("branch", gitProperties.map(GitProperties::getBranch).orElse("local"));

        return versionInfo;
    }
}
