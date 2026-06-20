package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Métadonnées d'une photo d'item Business Central
 * (réponse de {@code items({itemId})/picture}).
 *
 * Le préfixe de l'annotation média OData varie selon la version d'API BC
 * ("content@odata.mediaReadLink" en beta, "pictureContent@odata.mediaReadLink" en v2.0) :
 * on les capture donc dynamiquement plutôt que par un nom fixe.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcItemPicture {

    @JsonProperty("id")
    private String id;

    @JsonProperty("width")
    private Integer width;

    @JsonProperty("height")
    private Integer height;

    @JsonProperty("contentType")
    private String contentType;

    /** Liens média OData (mediaReadLink / mediaEditLink), tous préfixes confondus. */
    @JsonIgnore
    private final Map<String, String> mediaLinks = new HashMap<>();

    @JsonAnySetter
    public void setDynamic(String key, Object value) {
        if (key != null && value != null && key.contains("@odata.media")) {
            mediaLinks.put(key, String.valueOf(value));
        }
    }

    /** Lien de lecture du contenu binaire renvoyé par BC (ou null). */
    @JsonIgnore
    public String getContentReadLink() {
        return mediaLinks.entrySet().stream()
                .filter(e -> e.getKey().contains("mediaReadLink"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** true si BC a renvoyé une photo exploitable (id présent). */
    @JsonIgnore
    public boolean hasContent() {
        return id != null && !id.isBlank();
    }
}
