package goshkow.addhead;

import goshkow.addhead.api.AddHeadsProvider;
import goshkow.addhead.api.HeadFormat;
import goshkow.addhead.api.HeadRenderOptions;
import goshkow.addhead.api.HeadRenderTarget;
import goshkow.addhead.api.SkinTexture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.UUID;

/**
 * Internal implementation of the public AddHeads service API.
 */
public final class AddHeadsApiProvider implements AddHeadsProvider {

    private final AddHeads plugin;

    public AddHeadsApiProvider(AddHeads plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public HeadRenderOptions getDefaultOptions(HeadRenderTarget target) {
        return switch (target == null ? HeadRenderTarget.CUSTOM : target) {
            case CHAT -> HeadRenderOptions.chat(plugin.getChatHeadSpacing(), plugin.isChatHeadShadowEnabled());
            case TAB -> HeadRenderOptions.tab(plugin.getTabHeadSpacing(), plugin.isTabHeadShadowEnabled());
            case CUSTOM -> HeadRenderOptions.custom(0, true);
        };
    }

    @Override
    public SkinTexture getSkinTexture(UUID playerId, String playerName) {
        Utils.TextureData texture = Utils.getCurrentTextureData(plugin.getSkinService(), playerId, playerName);
        if (texture == null) {
            return null;
        }
        return new SkinTexture(texture.value(), texture.signature());
    }

    @Override
    public boolean isSkinReady(UUID playerId, String playerName) {
        return getSkinTexture(playerId, playerName) != null;
    }

    @Override
    public Component getHeadComponent(UUID playerId, String playerName, HeadRenderOptions options) {
        HeadRenderOptions resolved = options == null ? getDefaultOptions(HeadRenderTarget.CUSTOM) : options;
        return switch (resolved.target()) {
            case TAB -> Utils.createTabHeadComponent(plugin.getSkinService(), playerId, playerName, resolved.shadowEnabled());
            case CHAT, CUSTOM -> Utils.createHeadComponent(plugin.getSkinService(), playerId, playerName, resolved.shadowEnabled());
        };
    }

    @Override
    public Component prependHead(Component message, UUID playerId, String playerName, HeadRenderOptions options) {
        HeadRenderOptions resolved = options == null ? getDefaultOptions(HeadRenderTarget.CUSTOM) : options;
        Component safeMessage = message == null ? Component.empty() : message;
        return switch (resolved.target()) {
            case TAB -> Utils.prependTabHead(plugin.getSkinService(), safeMessage, playerId, playerName, resolved.spacing(), resolved.shadowEnabled());
            case CHAT, CUSTOM -> Utils.prependHead(plugin.getSkinService(), safeMessage, playerId, playerName, resolved.spacing(), resolved.shadowEnabled());
        };
    }

    @Override
    public String getSeparatorText(HeadRenderOptions options) {
        HeadRenderOptions resolved = options == null ? getDefaultOptions(HeadRenderTarget.CUSTOM) : options;
        return Utils.buildHeadSeparatorText(resolved.spacing());
    }

    @Override
    public String getFormattedHead(UUID playerId, String playerName, HeadFormat format, HeadRenderOptions options) {
        if (format == null) {
            return "";
        }
        HeadRenderOptions resolved = options == null ? getDefaultOptions(HeadRenderTarget.CUSTOM) : options;
        return switch (format) {
            case JSON_COMPONENT -> GsonComponentSerializer.gson().serialize(getHeadComponent(playerId, playerName, resolved));
            case SIGNED_TAG -> Utils.buildTabSignedTexture(plugin.getSkinService(), playerId, playerName);
            case TEXTURE_TAG -> Utils.buildTabTexture(plugin.getSkinService(), playerId, playerName);
            case ID_TAG -> Utils.buildTabId(playerId);
            case NAME_TAG -> Utils.buildTabName(playerName);
            case TEXTURE_VALUE -> {
                SkinTexture texture = getSkinTexture(playerId, playerName);
                yield texture != null ? texture.value() : "";
            }
            case TEXTURE_SIGNATURE -> {
                SkinTexture texture = getSkinTexture(playerId, playerName);
                yield texture != null && texture.signature() != null ? texture.signature() : "";
            }
            case TEXTURE_HASH -> {
                String tag = Utils.buildTabTexture(plugin.getSkinService(), playerId, playerName);
                yield tag.startsWith("<head:texture:") && tag.endsWith(">")
                        ? tag.substring("<head:texture:".length(), tag.length() - 1)
                        : "";
            }
            case SIGNED_TEXTURE -> Utils.buildSignedTextureValue(plugin.getSkinService(), playerId, playerName);
            case SEPARATOR -> getSeparatorText(resolved);
            case SKIN_READY -> isSkinReady(playerId, playerName) ? "true" : "false";
        };
    }
}
