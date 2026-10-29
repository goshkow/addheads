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
        // Runs on the joining player's thread, which is the only place where
        // that player may be messaged on Folia.
        Schedulers.runForLater(plugin, event.getPlayer(), () -> {
            if (!event.getPlayer().isOnline()) {
                return;
            }
            boolean canSeeUpdateNotice = plugin.shouldReceiveUpdateNotifications(event.getPlayer());

            if (!canSeeUpdateNotice) {
                return;
            }

            plugin.getUpdateCheckerService().notifyPlayerIfPending(event.getPlayer());

            if (plugin.isTabListFormattingLikelyEnabled() && !plugin.isTabFormattingCompatibilityEnabled()) {
                event.getPlayer().sendMessage(plugin.message("TAB plugin detected with tablist-name-formatting.enabled=true. This mode can block AddHeads from adding heads over formatted tab names."));
                event.getPlayer().sendMessage(plugin.message("If heads still do not appear, restart the server so TAB API compatibility can initialize, or disable TAB's tablist-name-formatting."));
            }
        }, 40L);
    }
}
