package goshkow.addhead.api;

import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Public service API exposed by AddHeads for other plugins.
 */
public interface AddHeadsProvider {

    String getPluginVersion();

    HeadRenderOptions getDefaultOptions(HeadRenderTarget target);

    SkinTexture getSkinTexture(UUID playerId, String playerName);

    default SkinTexture getSkinTexture(OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        return getSkinTexture(player.getUniqueId(), player.getName());
    }

    boolean isSkinReady(UUID playerId, String playerName);

    default boolean isSkinReady(OfflinePlayer player) {
        if (player == null) {
            return false;
        }
        return isSkinReady(player.getUniqueId(), player.getName());
    }

    Component getHeadComponent(UUID playerId, String playerName, HeadRenderOptions options);

    default Component getHeadComponent(OfflinePlayer player, HeadRenderOptions options) {
        if (player == null) {
            return Component.empty();
        }
        return getHeadComponent(player.getUniqueId(), player.getName(), options);
    }

    Component prependHead(Component message, UUID playerId, String playerName, HeadRenderOptions options);

    default Component prependHead(Component message, OfflinePlayer player, HeadRenderOptions options) {
        if (player == null) {
            return message;
        }
        return prependHead(message, player.getUniqueId(), player.getName(), options);
    }

    String getSeparatorText(HeadRenderOptions options);

    String getFormattedHead(UUID playerId, String playerName, HeadFormat format, HeadRenderOptions options);

    default String getFormattedHead(OfflinePlayer player, HeadFormat format, HeadRenderOptions options) {
        if (player == null) {
            return "";
        }
        return getFormattedHead(player.getUniqueId(), player.getName(), format, options);
    }
}
