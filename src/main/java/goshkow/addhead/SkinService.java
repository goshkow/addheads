package goshkow.addhead;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;

/**
 * Caches skin texture data so placeholders and chat decoration do not need to
 * resolve the same values repeatedly on hot paths.
 *
 * <p>The service refreshes online players on a configurable interval and can be
 * invalidated manually through the reload command or player lifecycle events.</p>
 */
public final class SkinService {

    private final AddHeads plugin;
    private final ConcurrentMap<UUID, Utils.TextureData> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Utils.TextureData>> pendingMojangLookups = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private int refreshTaskId = -1;

    public SkinService(AddHeads plugin) {
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
            if (cache.remove(playerId) != null) {
                notifyTabProfileUpdated(playerId);
            }
            triggerPaperProfileLookup(playerId, playerName);
            triggerMojangLookup(playerId, playerName);
        } else {
            Utils.TextureData existing = cache.put(playerId, data);
            if (!Objects.equals(existing, data)) {
                notifySkinResolved(playerId, playerName, data);
                notifyTabProfileUpdated(playerId);
            }
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
        plugin.refreshAllTabListNames();
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
                Utils.TextureData existing = cache.put(playerId, resolved);
                if (!Objects.equals(existing, resolved)) {
                    notifySkinResolved(playerId, playerName, resolved);
                }
            }
            return resolved;
        }

        Utils.TextureData asyncResolved = resolveTextureWithoutLivePlayer(playerId, playerName);
        if (asyncResolved != null) {
            Utils.TextureData existing = cache.put(playerId, asyncResolved);
            if (!Objects.equals(existing, asyncResolved)) {
                notifySkinResolved(playerId, playerName, asyncResolved);
            }
            return asyncResolved;
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

    private Utils.TextureData resolveTextureWithoutLivePlayer(UUID playerId, String playerName) {
        String resolvedName = Utils.resolveName(playerId, playerName);
        List<ResolvedProperty> skinsRestorerProperties = getSkinsRestorerProperties(playerId, resolvedName);
        if (!skinsRestorerProperties.isEmpty()) {
            return toTextureData(skinsRestorerProperties);
        }
        return null;
    }

    private void triggerMojangLookup(UUID playerId, String playerName) {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return;
        }
        pendingMojangLookups.computeIfAbsent(playerId, ignored -> CompletableFuture
                .supplyAsync(() -> fetchMojangTexture(playerName))
                .whenComplete((textureData, throwable) -> {
                    pendingMojangLookups.remove(playerId);
                    if (throwable != null || textureData == null || textureData.value() == null || textureData.value().isBlank()) {
                        return;
                    }
                    Utils.TextureData existing = cache.put(playerId, textureData);
                    if (!Objects.equals(existing, textureData)) {
                        notifySkinResolved(playerId, playerName, textureData);
                    }
                    notifyTabProfileUpdated(playerId);
                }));
    }

    private void triggerPaperProfileLookup(UUID playerId, String playerName) {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerProfile profile = Bukkit.createProfileExact(playerId, playerName);
                profile.update().whenComplete((updatedProfile, throwable) -> {
                    if (throwable != null || updatedProfile == null) {
                        return;
                    }

                    Utils.TextureData textureData = toTextureDataFromPaperProfile(updatedProfile.getProperties());
                    if (textureData == null || textureData.value() == null || textureData.value().isBlank()) {
                        return;
                    }

                    Utils.TextureData existing = cache.put(playerId, textureData);
                    if (!Objects.equals(existing, textureData)) {
                        notifySkinResolved(playerId, playerName, textureData);
                    }
                    notifyTabProfileUpdated(playerId);
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().fine("Paper profile lookup failed for " + playerName + ": " + exception.getMessage());
            }
        });
    }

    private Utils.TextureData fetchMojangTexture(String playerName) {
        try {
            String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            HttpRequest profileRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encodedName))
                    .GET()
                    .header("User-Agent", "AddHeads/" + plugin.getDescription().getVersion())
                    .build();

            HttpResponse<String> profileResponse = httpClient.send(profileRequest, HttpResponse.BodyHandlers.ofString());
            if (profileResponse.statusCode() != 200 || profileResponse.body() == null || profileResponse.body().isBlank()) {
                return null;
            }

            JsonElement profileElement = JsonParser.parseString(profileResponse.body());
            if (!profileElement.isJsonObject()) {
                return null;
            }

            JsonObject profileObject = profileElement.getAsJsonObject();
            String id = profileObject.has("id") ? profileObject.get("id").getAsString() : null;
            if (id == null || id.isBlank()) {
                return null;
            }

            HttpRequest textureRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false"))
                    .GET()
                    .header("User-Agent", "AddHeads/" + plugin.getDescription().getVersion())
                    .build();

            HttpResponse<String> textureResponse = httpClient.send(textureRequest, HttpResponse.BodyHandlers.ofString());
            if (textureResponse.statusCode() != 200 || textureResponse.body() == null || textureResponse.body().isBlank()) {
                return null;
            }

            JsonElement textureElement = JsonParser.parseString(textureResponse.body());
            if (!textureElement.isJsonObject()) {
                return null;
            }

            JsonObject textureObject = textureElement.getAsJsonObject();
            if (!textureObject.has("properties") || !textureObject.get("properties").isJsonArray()) {
                return null;
            }

            for (JsonElement propertyElement : textureObject.getAsJsonArray("properties")) {
                if (!propertyElement.isJsonObject()) {
                    continue;
                }
                JsonObject propertyObject = propertyElement.getAsJsonObject();
                String name = propertyObject.has("name") ? propertyObject.get("name").getAsString() : null;
                String value = propertyObject.has("value") ? propertyObject.get("value").getAsString() : null;
                String signature = propertyObject.has("signature") ? propertyObject.get("signature").getAsString() : null;
                if ("textures".equals(name) && value != null && !value.isBlank()) {
                    return new Utils.TextureData(value, signature);
                }
            }
        } catch (IOException | InterruptedException | RuntimeException exception) {
            plugin.getLogger().fine("Mojang texture lookup failed for " + playerName + ": " + exception.getMessage());
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

    private Utils.TextureData toTextureDataFromPaperProfile(Collection<?> properties) {
        for (Object property : properties) {
            String name = invokeString(property, "getName");
            String value = invokeString(property, "getValue");
            String signature = invokeString(property, "getSignature");
            if ("textures".equals(name) && value != null && !value.isBlank()) {
                return new Utils.TextureData(value, signature);
            }
        }
        return null;
    }

    private void notifyTabProfileUpdated(UUID playerId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                plugin.refreshTabListName(player);
            }
        });
    }

    private void notifySkinResolved(UUID playerId, String playerName, Utils.TextureData textureData) {
        plugin.fireSkinResolvedEvent(playerId, playerName, textureData);
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
