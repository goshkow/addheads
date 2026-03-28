package goshkow.addhead;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the skin cache in sync with player lifecycle events.
 */
public final class SkinSyncListener implements Listener {

    private final AddHeads plugin;
    private final SkinService skinService;

    public SkinSyncListener(AddHeads plugin, SkinService skinService) {
        this.plugin = plugin;
        this.skinService = skinService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.captureTabBaseName(event.getPlayer());
        plugin.syncPremiumDefaults(event.getPlayer());
        plugin.refreshTabListName(event.getPlayer());
        plugin.refreshTabListNameLater(event.getPlayer(), 10L);
        plugin.refreshTabListNameLater(event.getPlayer(), 40L);
        plugin.refreshTabListNameLater(event.getPlayer(), 100L);
        plugin.refreshTabListNameLater(event.getPlayer(), 200L);
        plugin.refreshTabListNameLater(event.getPlayer(), 400L);
        skinService.refresh(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        skinService.refreshLater(event.getPlayer().getUniqueId(), event.getPlayer().getName(), 10L);
        skinService.refreshLater(event.getPlayer().getUniqueId(), event.getPlayer().getName(), 40L);
        skinService.refreshLater(event.getPlayer().getUniqueId(), event.getPlayer().getName(), 100L);
        skinService.refreshLater(event.getPlayer().getUniqueId(), event.getPlayer().getName(), 200L);
        skinService.refreshLater(event.getPlayer().getUniqueId(), event.getPlayer().getName(), 400L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        skinService.invalidate(event.getPlayer().getUniqueId());
        plugin.forgetTabBaseName(event.getPlayer().getUniqueId());
    }
}
