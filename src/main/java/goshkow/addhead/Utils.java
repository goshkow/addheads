package goshkow.addhead;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared formatting and skin-resolution helpers.
 *
 * <p>All outputs resolve textures in the same order:
 * SkinsRestorer, live Paper profile, then the original wrapped profile.</p>
 */
public final class Utils {

    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");
    private static final String HEAD_SEPARATOR = "\u200C";
    private static final Pattern TEXTURE_URL_PATTERN =
            Pattern.compile("https?://textures\\.minecraft\\.net/texture/([A-Za-z0-9]+)");

    private Utils() {
    }

    public static Component prependHead(SkinService skinService, Component message, UUID playerId, String playerName) {
        return prependHead(skinService, message, playerId, playerName, 2, true);
    }

    public static Component prependHead(SkinService skinService, Component message, UUID playerId, String playerName, boolean appendSpace) {
        return prependHead(skinService, message, playerId, playerName, appendSpace ? 2 : 0, true);
    }

    public static Component prependHead(SkinService skinService, Component message, UUID playerId, String playerName, int separatorCount, boolean shadowEnabled) {
        Component builder = Component.empty()
                .append(createHeadComponent(skinService, playerId, playerName, shadowEnabled));
        if (separatorCount > 0) {
            builder = builder.append(headSeparator(separatorCount));
        }
        return builder.append(message);
    }

    public static Component prependTabHead(SkinService skinService, Component message, UUID playerId, String playerName, int separatorCount, boolean shadowEnabled) {
        Component builder = Component.empty()
                .append(createTabHeadComponent(skinService, playerId, playerName, shadowEnabled));
        if (separatorCount > 0) {
            builder = builder.append(tabSeparator(separatorCount));
        }
        return builder.append(message);
    }

    public static Component prependHead(Component message, UUID playerId, String playerName) {
        return prependHead(message, playerId, playerName, 2, true);
    }

    public static Component prependHead(Component message, UUID playerId, String playerName, boolean appendSpace) {
        return prependHead(message, playerId, playerName, appendSpace ? 2 : 0, true);
    }

    public static Component prependHead(Component message, UUID playerId, String playerName, int separatorCount, boolean shadowEnabled) {
        Component builder = Component.empty()
                .append(createHeadComponent(playerId, playerName, shadowEnabled));
        if (separatorCount > 0) {
            builder = builder.append(headSeparator(separatorCount));
        }
        return builder.append(message);
    }

    public static Component createHeadComponent(SkinService skinService, UUID playerId, String playerName) {
        return createHeadComponent(skinService, playerId, playerName, true);
    }

    public static Component createHeadComponent(SkinService skinService, UUID playerId, String playerName, boolean shadowEnabled) {
        String resolvedName = resolveName(playerId, playerName);
        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead()
                .name(resolvedName)
                .hat(true);

        if (playerId != null) {
            builder.id(playerId);
        }

        TextureData textureData = getCurrentTextureData(skinService, playerId, resolvedName);
        if (textureData != null) {
            applyResolvedProperties(builder, List.of(
                    new ResolvedProperty("textures", textureData.value, textureData.signature)
            ));
        }

        return applyHeadStyle(Component.object(builder.build()), resolvedName, shadowEnabled);
    }

    public static Component createTabHeadComponent(SkinService skinService, UUID playerId, String playerName, boolean shadowEnabled) {
        return resetTabHeadStyle(createHeadComponent(skinService, playerId, playerName, shadowEnabled));
    }

    public static Component createHeadComponent(UUID playerId, String playerName) {
        return createHeadComponent(playerId, playerName, true);
    }

    public static Component createHeadComponent(UUID playerId, String playerName, boolean shadowEnabled) {
        String resolvedName = resolveName(playerId, playerName);

        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead()
                .name(resolvedName)
                .hat(true);

        if (playerId != null) {
            builder.id(playerId);
        }

        Collection<ResolvedProperty> properties = resolveSkinProperties(playerId, resolvedName);
        if (!properties.isEmpty()) {
            applyResolvedProperties(builder, properties);
        }

        return applyHeadStyle(Component.object(builder.build()), resolvedName, shadowEnabled);
    }

    public static TextureData getCurrentTextureData(SkinService skinService, UUID playerId, String playerName) {
        if (skinService != null) {
            TextureData cached = skinService.getTexture(playerId, playerName);
            if (cached != null) {
                return cached;
            }
        }
        return null;
    }

    public static TextureData getCurrentTextureData(UUID playerId, String playerName) {
        String resolvedName = resolveName(playerId, playerName);
        for (ResolvedProperty property : resolveSkinProperties(playerId, resolvedName)) {
            if ("textures".equals(property.name) && property.value != null && !property.value.isBlank()) {
                return new TextureData(property.value, property.signature);
            }
        }
        return null;
    }

    public static String resolveName(UUID playerId, String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            return playerName;
        }
        if (playerId != null) {
            String offlineName = Bukkit.getOfflinePlayer(playerId).getName();
            if (offlineName != null && !offlineName.isBlank()) {
                return offlineName;
            }
            return playerId.toString();
        }
        return "Unknown";
    }

    public static String buildTabSignedTexture(SkinService skinService, UUID playerId, String playerName) {
        TextureData texture = getCurrentTextureData(skinService, playerId, playerName);
        if (texture == null || texture.signature == null || texture.signature.isBlank()) {
            return "";
        }
        return "<head:signed_texture:" + texture.value + ";" + texture.signature + ">";
    }

    public static String buildTabId(UUID playerId) {
        return playerId == null ? "" : "<head:id:" + playerId + ">";
    }

    public static String buildTabName(String playerName) {
        return playerName == null || playerName.isBlank() ? "" : "<head:name:" + playerName + ">";
    }

    public static String buildTabTexture(SkinService skinService, UUID playerId, String playerName) {
        TextureData texture = getCurrentTextureData(skinService, playerId, playerName);
        if (texture == null) {
            return "";
        }

        String hash = extractTextureHash(texture.value);
        return hash == null || hash.isBlank() ? "" : "<head:texture:" + hash + ">";
    }

    public static String buildSignedTextureValue(SkinService skinService, UUID playerId, String playerName) {
        TextureData texture = getCurrentTextureData(skinService, playerId, playerName);
        if (texture == null || texture.signature == null || texture.signature.isBlank()) {
            return "";
        }
        return texture.value + ";" + texture.signature;
    }

    private static Component applyHeadStyle(Component component, String resolvedName, boolean shadowEnabled) {
        Component styled = component.hoverEvent(HoverEvent.showText(Component.text(resolvedName)));
        if (!shadowEnabled) {
            styled = styled.shadowColor(ShadowColor.none());
        }
        return styled;
    }

    private static Component resetTabHeadStyle(Component component) {
        return component
                .font(DEFAULT_FONT)
                .colorIfAbsent(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.OBFUSCATED, false)
                .decoration(TextDecoration.STRIKETHROUGH, false)
                .decoration(TextDecoration.UNDERLINED, false);
    }

    public static String buildHeadSeparatorText(int separatorCount) {
        if (separatorCount <= 0) {
            return "";
        }
        return HEAD_SEPARATOR.repeat(separatorCount);
    }

    public static String buildTabHeadSeparatorMarkup(int separatorCount) {
        if (separatorCount <= 0) {
            return "";
        }
        return "&l" + HEAD_SEPARATOR.repeat(separatorCount) + "&r";
    }

    private static Component tabSeparator(int separatorCount) {
        return headSeparator(separatorCount);
    }

    private static Component headSeparator(int separatorCount) {
        return Component.text(buildHeadSeparatorText(separatorCount))
                .font(DEFAULT_FONT)
                .color(NamedTextColor.WHITE)
                .shadowColor(ShadowColor.none())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.OBFUSCATED, false)
                .decoration(TextDecoration.STRIKETHROUGH, false)
                .decoration(TextDecoration.UNDERLINED, false);
    }

    private static void applyResolvedProperties(PlayerHeadObjectContents.Builder builder, Collection<ResolvedProperty> properties) {
        for (ResolvedProperty property : properties) {
            if (property.name == null || property.value == null) {
                continue;
            }

            PlayerHeadObjectContents.ProfileProperty profileProperty = property.signature == null || property.signature.isBlank()
                    ? PlayerHeadObjectContents.property(property.name, property.value)
                    : PlayerHeadObjectContents.property(property.name, property.value, property.signature);
            builder.profileProperty(profileProperty);
        }
    }

    private static Collection<ResolvedProperty> resolveSkinProperties(UUID playerId, String playerName) {
        List<ResolvedProperty> skinsRestorerProperties = getSkinsRestorerProperties(playerId, playerName);
        if (!skinsRestorerProperties.isEmpty()) {
            return skinsRestorerProperties;
        }

        List<ResolvedProperty> liveProfileProperties = getOnlinePlayerProperties(playerId);
        if (!liveProfileProperties.isEmpty()) {
            return liveProfileProperties;
        }

        return List.of();
    }

    private static List<ResolvedProperty> getOnlinePlayerProperties(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return List.of();
        }

        PlayerProfile playerProfile = player.getPlayerProfile();
        if (playerProfile == null || !playerProfile.hasTextures()) {
            return List.of();
        }

        return toResolvedProperties(playerProfile.getProperties());
    }

    private static List<ResolvedProperty> getSkinsRestorerProperties(UUID playerId, String playerName) {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return List.of();
        }
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
            return List.of();
        }

        try {
            Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object playerStorage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            Object optionalProperty = playerStorage.getClass()
                    .getMethod("getSkinForPlayer", UUID.class, String.class)
                    .invoke(playerStorage, playerId, playerName);

            if (!(optionalProperty instanceof Optional<?> optional) || optional.isEmpty()) {
                return List.of();
            }

            Object property = optional.get();
            String value = invokeString(property, "getValue");
            if (value == null) {
                value = invokeString(property, "value");
            }

            String signature = invokeString(property, "getSignature");
            if (signature == null) {
                signature = invokeString(property, "signature");
            }

            if (value == null || value.isBlank()) {
                return List.of();
            }

            return List.of(new ResolvedProperty("textures", value, signature));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<ResolvedProperty> toResolvedProperties(Collection<?> properties) {
        List<ResolvedProperty> result = new ArrayList<>();
        for (Object property : properties) {
            String name = invokeString(property, "getName");
            String value = invokeString(property, "getValue");
            String signature = invokeString(property, "getSignature");
            if (name != null && value != null) {
                result.add(new ResolvedProperty(name, value, signature));
            }
        }
        return result;
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return Objects.toString(value, null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String extractTextureHash(String textureValue) {
        try {
            String decoded = new String(Base64.getDecoder().decode(textureValue), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL_PATTERN.matcher(decoded);
            return matcher.find() ? matcher.group(1) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ResolvedProperty(String name, String value, String signature) {
    }

    /**
     * Immutable texture payload used by placeholder formatters.
     */
    public record TextureData(String value, String signature) {
    }
}
