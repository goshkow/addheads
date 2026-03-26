package goshkow.addhead;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Administrative command entry point.
 */
public final class AddHeadsCommand implements CommandExecutor, TabCompleter {

    private final AddHeads plugin;

    public AddHeadsCommand(AddHeads plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("addhead.reload")) {
                sender.sendMessage(plugin.message("command.no-permission"));
                return true;
            }

            plugin.reloadPlugin();
            sender.sendMessage(plugin.message("command.reload.success"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("fixtab")) {
            if (!sender.hasPermission("addhead.reload")) {
                sender.sendMessage(plugin.message("command.no-permission"));
                return true;
            }

            AddHeads.TabFixResult result = plugin.fixTabMiniMessageSetting();
            sender.sendMessage(plugin.message(result.messageKey(), result.placeholders()));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("togglechat")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("command.players-only"));
                return true;
            }

            boolean enabled = plugin.toggleChatFor(player);
            sender.sendMessage(plugin.message(
                    enabled ? "command.togglechat.enabled" : "command.togglechat.disabled"
            ));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("toggletab")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("command.players-only"));
                return true;
            }

            boolean enabled = plugin.toggleTabFor(player);
            sender.sendMessage(plugin.message(
                    enabled ? "command.toggletab.enabled" : "command.toggletab.disabled"
            ));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(plugin.getLanguageManager().get("command.info.line1", Map.of(
                    "name", plugin.getDescription().getName(),
                    "version", plugin.getDescription().getVersion()
            )));
            sender.sendMessage(plugin.getLanguageManager().get("command.info.line2", Map.of(
                    "author", "goshkow"
            )));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("command.players-only"));
                return true;
            }
            if (!sender.hasPermission("addhead.settings")) {
                sender.sendMessage(plugin.message("command.no-permission"));
                return true;
            }

            plugin.openSettingsMenu(player);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("settings") && args[1].equalsIgnoreCase("cancel")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("command.players-only"));
                return true;
            }

            plugin.cancelSettingsInput(player);
            return true;
        }

        sender.sendMessage(plugin.message("command.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new java.util.ArrayList<>();
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);

            addSuggestion(suggestions, "togglechat", prefix);
            addSuggestion(suggestions, "toggletab", prefix);
            if (sender.hasPermission("addhead.settings")) {
                addSuggestion(suggestions, "settings", prefix);
            }
            if (sender.hasPermission("addhead.reload")) {
                addSuggestion(suggestions, "reload", prefix);
            }
            return suggestions;
        }
        return List.of();
    }

    private void addSuggestion(List<String> suggestions, String value, String prefix) {
        if (value.startsWith(prefix)) {
            suggestions.add(value);
        }
    }
}
