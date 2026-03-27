package goshkow.addhead;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Prepends a head object to the fully rendered chat message.
 *
 * <p>The listener runs at monitor priority so the plugin decorates the final
 * component without breaking formatting, links, hovers, or click events.</p>
 */
public final class AsyncChatListener implements Listener {

    private final AddHeads plugin;
    private final SkinService skinService;

    public AsyncChatListener(AddHeads plugin, SkinService skinService) {
        this.plugin = plugin;
        this.skinService = skinService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.isChatFeatureEnabled()) {
            return;
        }
        ChatRenderer currentRenderer = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            skinService.refreshLater(source.getUniqueId(), source.getName(), 1L);
            skinService.refreshLater(source.getUniqueId(), source.getName(), 20L);
            skinService.refreshLater(source.getUniqueId(), source.getName(), 60L);
            Component rendered = currentRenderer.render(source, sourceDisplayName, message, viewer);
            if (!plugin.shouldRenderChatHeadFor(viewer)) {
                return rendered;
            }
            return Utils.prependHead(skinService, rendered, source.getUniqueId(), source.getName(), plugin.isChatHeadSpaceEnabled());
        });
    }
}
