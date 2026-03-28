package goshkow.addhead;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles clicks and chat input for the settings menu.
 */
public final class SettingsMenuListener implements Listener {

    private final AddHeads plugin;
    private final SettingsMenu menu;
    private final SettingsSessionService sessions;

    public SettingsMenuListener(AddHeads plugin, SettingsMenu menu, SettingsSessionService sessions) {
        this.plugin = plugin;
        this.menu = menu;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        menu.handleClick(player, event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.getUpdateCheckerService() != null
                && plugin.getUpdateCheckerService().confirmPendingDownload(player, PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            event.setCancelled(true);
            return;
        }
        if (!sessions.isPending(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> menu.handleChatInput(player, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.clear(event.getPlayer().getUniqueId());
        if (plugin.getUpdateCheckerService() != null) {
            plugin.getUpdateCheckerService().clearPendingDownload(event.getPlayer().getUniqueId());
        }
    }
}
