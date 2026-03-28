package goshkow.addhead.api;

import org.bukkit.Bukkit;

/**
 * Static entry point for resolving the public AddHeads service.
 */
public final class AddHeadsAPI {

    private AddHeadsAPI() {
    }

    public static AddHeadsProvider provider() {
        return Bukkit.getServicesManager().load(AddHeadsProvider.class);
    }

    public static boolean isAvailable() {
        return provider() != null;
    }
}
