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

    private final SkinService skinService;

    public SkinSyncListener(SkinService skinService) {
        this.skinService = skinService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
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
    }
}
