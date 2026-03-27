package goshkow.addhead;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Notifies administrators about available updates.
 */
public final class AdminNoticeListener implements Listener {

    private final AddHeads plugin;

    public AdminNoticeListener(AddHeads plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) {
                return;
            }
            boolean canSeeUpdateNotice = plugin.shouldReceiveUpdateNotifications(event.getPlayer());

            if (!canSeeUpdateNotice) {
                return;
            }

            plugin.getUpdateCheckerService().notifyPlayerIfPending(event.getPlayer());
        }, 40L);
    }
}
