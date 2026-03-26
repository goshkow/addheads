package goshkow.addhead;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Caches skin texture data so placeholders and chat decoration do not need to
 * resolve the same values repeatedly on hot paths.
 *
 * <p>The service refreshes online players on a configurable interval and can be
 * invalidated manually through the reload command or player lifecycle events.</p>
 */
public final class SkinService {

    private final Plugin plugin;
    private final ConcurrentMap<UUID, Utils.TextureData> cache = new ConcurrentHashMap<>();
    private int refreshTaskId = -1;

    public SkinService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(long refreshTicks) {
        stop();
        refreshOnlinePlayers();
        if (refreshTicks > 0L) {
            refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin,
                    this::refreshOnlinePlayers,
                    refreshTicks,
                    refreshTicks
            );
        }
    }

    public void stop() {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
    }

    public void clear() {
        cache.clear();
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    public void refresh(UUID playerId, String playerName) {
        if (playerId == null) {
            return;
        }

        Utils.TextureData data = resolveTexture(playerId, playerName);
        if (data == null) {
            cache.remove(playerId);
        } else {
            cache.put(playerId, data);
        }
    }

    public void refreshLater(UUID playerId, String playerName, long delayTicks) {
        if (playerId == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(playerId, playerName), Math.max(0L, delayTicks));
    }

    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player.getUniqueId(), player.getName());
        }
    }

    public Utils.TextureData getTexture(UUID playerId, String playerName) {
        if (playerId == null) {
            return null;
        }
        Utils.TextureData cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }

        if (Bukkit.isPrimaryThread()) {
            Utils.TextureData resolved = resolveTexture(playerId, playerName);
            if (resolved != null) {
                cache.put(playerId, resolved);
            }
            return resolved;
        }

        return null;
    }

    private Utils.TextureData resolveTexture(UUID playerId, String playerName) {
        String resolvedName = Utils.resolveName(playerId, playerName);
        List<ResolvedProperty> skinsRestorerProperties = getSkinsRestorerProperties(playerId, resolvedName);
        if (!skinsRestorerProperties.isEmpty()) {
            return toTextureData(skinsRestorerProperties);
        }

        List<ResolvedProperty> liveProfileProperties = getOnlinePlayerProperties(playerId);
        if (!liveProfileProperties.isEmpty()) {
            return toTextureData(liveProfileProperties);
        }

        return null;
    }

    private List<ResolvedProperty> getOnlinePlayerProperties(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return List.of();
        }

        PlayerProfile playerProfile = player.getPlayerProfile();
        if (playerProfile == null || !playerProfile.hasTextures()) {
            return List.of();
        }

        return toResolvedProperties(playerProfile.getProperties());
    }

    private List<ResolvedProperty> getSkinsRestorerProperties(UUID playerId, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return List.of();
        }
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
            return List.of();
        }

        try {
            Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object playerStorage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            Object optionalProperty = playerStorage.getClass()
                    .getMethod("getSkinForPlayer", UUID.class, String.class)
                    .invoke(playerStorage, playerId, playerName);

            if (!(optionalProperty instanceof Optional<?> optional) || optional.isEmpty()) {
                return List.of();
            }

            Object property = optional.get();
            String value = invokeString(property, "getValue");
            if (value == null) {
                value = invokeString(property, "value");
            }

            String signature = invokeString(property, "getSignature");
            if (signature == null) {
                signature = invokeString(property, "signature");
            }

            if (value == null || value.isBlank()) {
                return List.of();
            }

            return List.of(new ResolvedProperty("textures", value, signature));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    private List<ResolvedProperty> toResolvedProperties(Collection<?> properties) {
        List<ResolvedProperty> result = new ArrayList<>();
        for (Object property : properties) {
            String name = invokeString(property, "getName");
            String value = invokeString(property, "getValue");
            String signature = invokeString(property, "getSignature");
            if (name != null && value != null) {
                result.add(new ResolvedProperty(name, value, signature));
            }
        }
        return result;
    }

    private Utils.TextureData toTextureData(List<ResolvedProperty> properties) {
        for (ResolvedProperty property : properties) {
            if ("textures".equals(property.name) && property.value != null && !property.value.isBlank()) {
                return new Utils.TextureData(property.value, property.signature);
            }
        }
        return null;
    }

    private String invokeString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private record ResolvedProperty(String name, String value, String signature) {
    }
}
