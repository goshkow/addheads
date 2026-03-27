package goshkow.addhead;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

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
            return GsonComponentSerializer.gson().serialize(
                    Utils.createHeadComponent(plugin.getSkinService(), player.getUniqueId(), player.getName())
            );
        }

        if (identifier.equals("tab")) {
            if (!plugin.shouldRenderTabHeadFor(offlinePlayer.getUniqueId())) {
                return "";
            }

            String signedTexture = Utils.buildTabSignedTexture(plugin.getSkinService(), offlinePlayer.getUniqueId(), offlinePlayer.getName());
            if (!signedTexture.isBlank()) {
                return plugin.isTabHeadSpaceEnabled() ? signedTexture + " " : signedTexture;
            }
            return "";
        }

        if (identifier.equals("texture_value")) {
            Utils.TextureData texture = Utils.getCurrentTextureData(plugin.getSkinService(), offlinePlayer.getUniqueId(), offlinePlayer.getName());
            return texture != null ? texture.value() : "";
        }

        if (identifier.equals("texture_signature")) {
            Utils.TextureData texture = Utils.getCurrentTextureData(plugin.getSkinService(), offlinePlayer.getUniqueId(), offlinePlayer.getName());
            return texture != null && texture.signature() != null ? texture.signature() : "";
        }

        if (identifier.equals("texture_hash")) {
            String tabTexture = Utils.buildTabTexture(plugin.getSkinService(), offlinePlayer.getUniqueId(), offlinePlayer.getName());
            if (tabTexture.startsWith("<head:texture:") && tabTexture.endsWith(">")) {
                return tabTexture.substring("<head:texture:".length(), tabTexture.length() - 1);
            }
            return "";
        }

        if (identifier.equals("tab_visible")) {
            return plugin.shouldRenderTabHeadFor(offlinePlayer.getUniqueId()) ? "true" : "false";
        }

        if (identifier.equals("skin_ready")) {
            return Utils.getCurrentTextureData(plugin.getSkinService(), offlinePlayer.getUniqueId(), offlinePlayer.getName()) != null
                    ? "true"
                    : "false";
        }

        return null;
    }
}
