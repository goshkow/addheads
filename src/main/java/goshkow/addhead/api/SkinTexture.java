package goshkow.addhead.api;

/**
 * Immutable signed or unsigned skin texture payload.
 *
 * @param value base64 texture value
 * @param signature Mojang/skin-provider signature, may be null
 */
public record SkinTexture(String value, String signature) {
}
