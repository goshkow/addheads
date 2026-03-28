package goshkow.addhead;

import goshkow.addhead.api.HeadFormat;
import goshkow.addhead.api.HeadRenderOptions;
import goshkow.addhead.api.HeadRenderTarget;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * PlaceholderAPI bridge for AddHeads.
 *
 * <p>Different plugins expect different syntaxes, so the expansion exposes
 * multiple explicit formats instead of one overloaded placeholder.</p>
 */
public final class HeadPlaceholderExpansion extends PlaceholderExpansion {

    private final AddHeads plugin;

    public HeadPlaceholderExpansion(AddHeads plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "addhead";
    }

    @Override
    public String getName() {
        return "player_head";
    }

    @Override
    public String getAuthor() {
        return "goshkow";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String identifier) {
        if (offlinePlayer == null) {
            return null;
        }
        if (!plugin.isPlaceholderFeatureEnabled()) {
            return "";
        }

        Player player = offlinePlayer.getPlayer();
        if (identifier.equals("head")) {
            if (player == null) {
                return "";
            }
            return plugin.getApiProvider().getFormattedHead(
                    player.getUniqueId(),
                    player.getName(),
                    HeadFormat.JSON_COMPONENT,
                    plugin.getApiProvider().getDefaultOptions(HeadRenderTarget.CHAT)
            );
        }

        if (identifier.equals("tab")) {
            if (!plugin.shouldRenderTabHeadFor(offlinePlayer.getUniqueId())) {
                return "";
            }
            String signedTag = plugin.getApiProvider().getFormattedHead(
                    offlinePlayer.getUniqueId(),
                    offlinePlayer.getName(),
                    HeadFormat.SIGNED_TAG,
                    plugin.getApiProvider().getDefaultOptions(HeadRenderTarget.TAB)
            );
            if (signedTag.isBlank()) {
                return "";
            }
            return signedTag + plugin.getApiProvider().getSeparatorText(plugin.getApiProvider().getDefaultOptions(HeadRenderTarget.TAB));
        }

        if (identifier.equals("texture_value")) {
            return plugin.getApiProvider().getFormattedHead(
                    offlinePlayer.getUniqueId(),
                    offlinePlayer.getName(),
                    HeadFormat.TEXTURE_VALUE,
                    plugin.getApiProvider().getDefaultOptions(HeadRenderTarget.CUSTOM)
            );
        }

        if (identifier.equals("texture_signature")) {
            return plugin.getApiProvider().getFormattedHead(
                    offlinePlayer.getUniqueId(),
                    offlinePlayer.getName(),
                    HeadFormat.TEXTURE_SIGNATURE,
                    plugin.getApiProvider().getDefaultOptions(HeadRenderTarget.CUSTOM)
            );
        }

        if (identifier.equals("texture_hash")) {
            return plugin.getApiProvider().getFormattedHead(
                    offlinePlayer.getUniqueId(),
                    offlinePlayer.getName(),
                    HeadFormat.TEXTURE_HASH,
                    plugin.getApiProvider().getDefaultOptions(HeadRenderTarget.CUSTOM)
            );
        }

        if (identifier.equals("tab_visible")) {
            return plugin.shouldRenderTabHeadFor(offlinePlayer.getUniqueId()) ? "true" : "false";
        }

        if (identifier.equals("skin_ready")) {
            return plugin.getApiProvider().getFormattedHead(
                    offlinePlayer.getUniqueId(),
                    offlinePlayer.getName(),
                    HeadFormat.SKIN_READY,
                    plugin.getApiProvider().getDefaultOptions(HeadRenderTarget.CUSTOM)
            );
        }

        if (identifier.startsWith("format_")) {
            return formatPlaceholder(offlinePlayer, identifier.substring("format_".length()), HeadRenderTarget.CUSTOM);
        }

        if (identifier.startsWith("chat_")) {
            return formatPlaceholder(offlinePlayer, identifier.substring("chat_".length()), HeadRenderTarget.CHAT);
        }

        if (identifier.startsWith("tab_")) {
            if (!plugin.shouldRenderTabHeadFor(offlinePlayer.getUniqueId())) {
                return "";
            }
            return formatPlaceholder(offlinePlayer, identifier.substring("tab_".length()), HeadRenderTarget.TAB);
        }

        return null;
    }

    private String formatPlaceholder(OfflinePlayer player, String formatKey, HeadRenderTarget target) {
        Optional<HeadFormat> format = HeadFormat.fromKey(formatKey);
        if (format.isEmpty()) {
            return null;
        }

        HeadRenderOptions options = plugin.getApiProvider().getDefaultOptions(target);
        return plugin.getApiProvider().getFormattedHead(player.getUniqueId(), player.getName(), format.get(), options);
    }
}
