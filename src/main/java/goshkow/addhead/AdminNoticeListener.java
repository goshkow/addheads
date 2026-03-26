package goshkow.addhead;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Notifies administrators when TAB is configured in a way that breaks head placeholders.
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
            if (!event.getPlayer().hasPermission("addhead.reload")) {
                return;
            }
            if (!plugin.isTabMiniMessageEnabled()) {
                return;
            }

            Component message = Component.text()
                    .append(Component.text("[AddHeads] ", NamedTextColor.AQUA))
                    .append(Component.text(plugin.getLanguageManager().get("admin.tab-warning.line1"), NamedTextColor.WHITE))
                    .append(Component.space())
                    .append(Component.text(plugin.getLanguageManager().get("admin.tab-warning.line2"), NamedTextColor.GRAY))
                    .append(Component.space())
                    .append(
                            Component.text(plugin.getLanguageManager().get("admin.tab-warning.button"), NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.runCommand("/hd fixtab"))
                                    .hoverEvent(HoverEvent.showText(
                                            Component.text(plugin.getLanguageManager().get("admin.tab-warning.hover"), NamedTextColor.WHITE)
                                    ))
                    )
                    .build();

            event.getPlayer().sendMessage(message);
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.35f);
        }, 40L);
    }
}
