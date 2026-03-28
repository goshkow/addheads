package goshkow.addhead.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Export formats supported by AddHeads for API and placeholders.
 */
public enum HeadFormat {
    JSON_COMPONENT("json"),
    SIGNED_TAG("signed_tag"),
    TEXTURE_TAG("texture_tag"),
    ID_TAG("id_tag"),
    NAME_TAG("name_tag"),
    TEXTURE_VALUE("texture_value"),
    TEXTURE_SIGNATURE("texture_signature"),
    TEXTURE_HASH("texture_hash"),
    SIGNED_TEXTURE("signed_texture"),
    SEPARATOR("separator"),
    SKIN_READY("skin_ready");

    private final String key;

    HeadFormat(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<HeadFormat> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> format.key.equals(normalized))
                .findFirst();
    }
}
