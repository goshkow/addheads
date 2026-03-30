package goshkow.addhead;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Integrates AddHeads directly into TAB's own tablist formatting pipeline.
 *
 * <p>When TAB's tablist-name-formatting feature is enabled, TAB blocks
 * external display-name rewrites. Instead of fighting that, this bridge
 * registers a relational TAB placeholder and prepends it through TAB's
 * own TabListFormatManager.</p>
 */
public final class TabApiBridge {

    private static final String RELATIONAL_PLACEHOLDER = "%rel_addhead_tab_head%";
    private static final int PLACEHOLDER_REFRESH_MS = 1000;
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final AddHeads plugin;
    private Plugin tabPlugin;
    private ClassLoader classLoader;
    private Object tabApi;
    private Object eventBus;
    private Object placeholderManager;
    private Class<?> eventHandlerClass;
    private Object relationalPlaceholder;
    private Object playerLoadHandler;
    private Object tabLoadHandler;

    public TabApiBridge(AddHeads plugin) {
        this.plugin = plugin;
    }

    public boolean start() {
        tabPlugin = Bukkit.getPluginManager().getPlugin("TAB");
        if (tabPlugin == null) {
            return false;
        }

        try {
            classLoader = tabPlugin.getClass().getClassLoader();
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI", true, classLoader);
            tabApi = tabApiClass.getMethod("getInstance").invoke(null);
            if (tabApi == null) {
                return false;
            }

            eventBus = invoke(tabApi, "getEventBus");
            placeholderManager = invoke(tabApi, "getPlaceholderManager");
            eventHandlerClass = Class.forName("me.neznamy.tab.api.event.EventHandler", true, classLoader);

            if (!registerRelationalPlaceholder()) {
                stop();
                return false;
            }

            registerHandlers();
            Bukkit.getScheduler().runTask(plugin, this::refreshAll);
            Bukkit.getScheduler().runTaskLater(plugin, this::refreshAll, 40L);
            plugin.getLogger().info("TAB detected. Direct TAB API tablist compatibility is enabled.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Failed to initialize TAB API compatibility: " + exception.getMessage());
            stop();
            return false;
        }
    }

    public void stop() {
        clearAll();
        unregister(playerLoadHandler);
        unregister(tabLoadHandler);
        unregisterPlaceholder();
        playerLoadHandler = null;
        tabLoadHandler = null;
        relationalPlaceholder = null;
        eventHandlerClass = null;
        placeholderManager = null;
        eventBus = null;
        tabApi = null;
        classLoader = null;
        tabPlugin = null;
    }

    public boolean isAvailable() {
        return tabApi != null;
    }

    public boolean isManagingTabFormatting() {
        return isAvailable() && invoke(tabApi, "getTabListFormatManager") != null;
    }

    public Component resolveBaseComponent(UUID playerId, String playerName) {
        Object tabPlayer = getTabPlayer(playerId, playerName);
        if (tabPlayer == null) {
            return null;
        }

        String prefix = resolveEffectivePrefix(tabPlayer);
        String name = resolveCurrentValue(tabPlayer, "getCustomName", "getOriginalRawName", playerName);
        String suffix = resolveCurrentValue(tabPlayer, "getCustomSuffix", "getOriginalRawSuffix", "");
        String merged = nullToEmpty(prefix) + nullToEmpty(name) + nullToEmpty(suffix);
        if (merged.isBlank()) {
            return playerName != null && !playerName.isBlank() ? Component.text(playerName) : null;
        }
        return LEGACY_AMPERSAND.deserialize(merged);
    }

    public void refreshViewer(Player viewer) {
        if (!isManagingTabFormatting() || viewer == null) {
            return;
        }

        Object viewerTabPlayer = getTabPlayer(viewer.getUniqueId(), viewer.getName());
        if (viewerTabPlayer == null) {
            return;
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            Object targetTabPlayer = getTabPlayer(target.getUniqueId(), target.getName());
            if (targetTabPlayer == null) {
                continue;
            }
            updateRelationalPlaceholder(viewerTabPlayer, targetTabPlayer);
        }
    }

    public void refreshTarget(Player target) {
        if (!isManagingTabFormatting() || target == null) {
            return;
        }

        Object targetTabPlayer = getTabPlayer(target.getUniqueId(), target.getName());
        if (targetTabPlayer == null) {
            return;
        }

        applyFormatting(targetTabPlayer);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Object viewerTabPlayer = getTabPlayer(viewer.getUniqueId(), viewer.getName());
            if (viewerTabPlayer == null) {
                continue;
            }
            updateRelationalPlaceholder(viewerTabPlayer, targetTabPlayer);
        }
    }

    public void clearTarget(Player target) {
        if (!isManagingTabFormatting() || target == null) {
            return;
        }

        Object targetTabPlayer = getTabPlayer(target.getUniqueId(), target.getName());
        if (targetTabPlayer != null) {
            resetFormatting(targetTabPlayer);
        }
    }

    public void refreshAll() {
        if (!isManagingTabFormatting()) {
            return;
        }

        Object[] tabPlayers = getOnlineTabPlayers();
        for (Object tabPlayer : tabPlayers) {
            applyFormatting(tabPlayer);
        }

        for (Object viewer : tabPlayers) {
            for (Object target : tabPlayers) {
                updateRelationalPlaceholder(viewer, target);
            }
        }
    }

    private boolean registerRelationalPlaceholder() {
        if (placeholderManager == null) {
            return false;
        }

        try {
            Class<?> biFunctionClass = Class.forName("java.util.function.BiFunction");
            Object function = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{biFunctionClass},
                    (proxy, method, args) -> {
                        if ("apply".equals(method.getName()) && args != null && args.length == 2) {
                            return buildHeadValue(args[0], args[1]);
                        }
                        if (method.getDeclaringClass() == Object.class) {
                            return objectMethod(proxy, method.getName(), args);
                        }
                        return null;
                    }
            );

            Method method = placeholderManager.getClass().getMethod(
                    "registerRelationalPlaceholder",
                    String.class,
                    int.class,
                    java.util.function.BiFunction.class
            );
            relationalPlaceholder = method.invoke(placeholderManager, RELATIONAL_PLACEHOLDER, PLACEHOLDER_REFRESH_MS, function);
            return relationalPlaceholder != null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Failed to register TAB relational placeholder: " + exception.getMessage());
            relationalPlaceholder = null;
            return false;
        }
    }

    private void unregisterPlaceholder() {
        if (placeholderManager == null || relationalPlaceholder == null) {
            return;
        }
        try {
            Method method = placeholderManager.getClass().getMethod("unregisterPlaceholder", Class.forName("me.neznamy.tab.api.placeholder.Placeholder", true, classLoader));
            method.invoke(placeholderManager, relationalPlaceholder);
        } catch (ReflectiveOperationException ignored) {
            try {
                placeholderManager.getClass().getMethod("unregisterPlaceholder", String.class)
                        .invoke(placeholderManager, RELATIONAL_PLACEHOLDER);
            } catch (ReflectiveOperationException ignoredAgain) {
                // Best effort only.
            }
        }
    }

    private void registerHandlers() throws ReflectiveOperationException {
        if (eventBus == null || eventHandlerClass == null) {
            return;
        }

        Class<?> eventBusClass = Class.forName("me.neznamy.tab.api.event.EventBus", true, classLoader);
        Class<?> playerLoadEventClass = Class.forName("me.neznamy.tab.api.event.player.PlayerLoadEvent", true, classLoader);
        Class<?> tabLoadEventClass = Class.forName("me.neznamy.tab.api.event.plugin.TabLoadEvent", true, classLoader);

        playerLoadHandler = createHandler(this::handlePlayerLoad);
        tabLoadHandler = createHandler(event -> handleTabLoad());

        Method registerMethod = eventBusClass.getMethod("register", Class.class, eventHandlerClass);
        registerMethod.invoke(eventBus, playerLoadEventClass, playerLoadHandler);
        registerMethod.invoke(eventBus, tabLoadEventClass, tabLoadHandler);
    }

    private Object createHandler(Consumer<Object> consumer) {
        return Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{eventHandlerClass},
                (proxy, method, args) -> {
                    if ("handle".equals(method.getName()) && args != null && args.length == 1) {
                        consumer.accept(args[0]);
                        return null;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return objectMethod(proxy, method.getName(), args);
                    }
                    return null;
                }
        );
    }

    private Object objectMethod(Object proxy, String methodName, Object[] args) {
        return switch (methodName) {
            case "toString" -> "AddHeads-TAB-Handler";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args != null && args.length == 1 ? args[0] : null);
            default -> null;
        };
    }

    private void unregister(Object handler) {
        if (handler == null || eventBus == null || eventHandlerClass == null) {
            return;
        }
        try {
            Class<?> eventBusClass = Class.forName("me.neznamy.tab.api.event.EventBus", true, classLoader);
            eventBusClass.getMethod("unregister", eventHandlerClass).invoke(eventBus, handler);
        } catch (ReflectiveOperationException ignored) {
            // Best effort only.
        }
    }

    private void handlePlayerLoad(Object event) {
        UUID playerId = resolveEventPlayerId(event);
        if (playerId == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return;
            }
            plugin.forgetTabBaseName(playerId);
            refreshTarget(player);
            refreshViewer(player);
        });
    }

    private void handleTabLoad() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            placeholderManager = invoke(tabApi, "getPlaceholderManager");
            unregisterPlaceholder();
            registerRelationalPlaceholder();
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.forgetTabBaseName(player.getUniqueId());
            }
            refreshAll();
        });
    }

    private UUID resolveEventPlayerId(Object event) {
        Object tabPlayer = invoke(event, "getPlayer");
        if (tabPlayer == null) {
            return null;
        }

        Object uuid = invoke(tabPlayer, "getUniqueId");
        return uuid instanceof UUID id ? id : null;
    }

    private Object[] getOnlineTabPlayers() {
        if (tabApi == null) {
            return new Object[0];
        }

        Object players = invoke(tabApi, "getOnlinePlayers");
        if (players instanceof Object[] array) {
            return array;
        }
        return new Object[0];
    }

    private Object getTabPlayer(UUID playerId, String playerName) {
        if (tabApi == null) {
            return null;
        }

        Object tabPlayer = null;
        if (playerId != null) {
            tabPlayer = invoke(tabApi, "getPlayer", UUID.class, playerId);
        }
        if (tabPlayer == null && playerName != null && !playerName.isBlank()) {
            tabPlayer = invoke(tabApi, "getPlayer", String.class, playerName);
        }
        return tabPlayer;
    }

    private void clearAll() {
        if (!isManagingTabFormatting()) {
            return;
        }
        for (Object tabPlayer : getOnlineTabPlayers()) {
            resetFormatting(tabPlayer);
        }
    }

    private void applyFormatting(Object tabPlayer) {
        Object formatManager = invoke(tabApi, "getTabListFormatManager");
        if (formatManager == null || tabPlayer == null) {
            return;
        }

        String basePrefix = resolveEffectivePrefix(tabPlayer);
        if (basePrefix == null) {
            basePrefix = "";
        }
        setStringValue(formatManager, "setPrefix", tabPlayer, RELATIONAL_PLACEHOLDER + basePrefix);
    }

    private void resetFormatting(Object tabPlayer) {
        Object formatManager = invoke(tabApi, "getTabListFormatManager");
        if (formatManager == null || tabPlayer == null) {
            return;
        }

        String currentCustom = invokeString(formatManager, "getCustomPrefix", tabPlayer);
        if (currentCustom == null) {
            return;
        }

        String stripped = stripOwnPlaceholder(currentCustom);
        if (stripped.equals(currentCustom)) {
            return;
        }

        String restored = stripped.isBlank() ? null : stripped;
        setStringValue(formatManager, "setPrefix", tabPlayer, restored);
    }

    private String resolveEffectivePrefix(Object tabPlayer) {
        Object formatManager = invoke(tabApi, "getTabListFormatManager");
        if (formatManager == null || tabPlayer == null) {
            return "";
        }

        String currentCustom = invokeString(formatManager, "getCustomPrefix", tabPlayer);
        if (currentCustom != null) {
            return stripOwnPlaceholder(currentCustom);
        }
        return invokeString(formatManager, "getOriginalRawPrefix", tabPlayer);
    }

    private String resolveCurrentValue(Object tabPlayer, String customMethod, String originalMethod, String fallback) {
        Object formatManager = invoke(tabApi, "getTabListFormatManager");
        if (formatManager == null || tabPlayer == null) {
            return fallback;
        }

        String currentCustom = invokeString(formatManager, customMethod, tabPlayer);
        if (currentCustom != null) {
            return currentCustom;
        }

        String original = invokeString(formatManager, originalMethod, tabPlayer);
        return original != null ? original : fallback;
    }

    private void updateRelationalPlaceholder(Object viewerTabPlayer, Object targetTabPlayer) {
        if (relationalPlaceholder == null || viewerTabPlayer == null || targetTabPlayer == null) {
            return;
        }

        try {
            for (Method method : relationalPlaceholder.getClass().getMethods()) {
                if ("update".equals(method.getName()) && method.getParameterCount() == 2) {
                    method.invoke(relationalPlaceholder, viewerTabPlayer, targetTabPlayer);
                    return;
                }
                if ("updateValue".equals(method.getName()) && method.getParameterCount() == 3) {
                    method.invoke(relationalPlaceholder, viewerTabPlayer, targetTabPlayer, buildHeadValue(viewerTabPlayer, targetTabPlayer));
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Manual update is best-effort. Periodic refresh still keeps the placeholder alive.
        }
    }

    private String buildHeadValue(Object viewerTabPlayer, Object targetTabPlayer) {
        UUID viewerId = resolveTabPlayerUuid(viewerTabPlayer);
        UUID targetId = resolveTabPlayerUuid(targetTabPlayer);
        String targetName = resolveTabPlayerName(targetTabPlayer);

        if (!plugin.shouldRenderTabHeadFor(viewerId)) {
            return "";
        }

        String head = plugin.getApiProvider().getFormattedHead(
                targetId,
                targetName,
                goshkow.addhead.api.HeadFormat.SIGNED_TAG,
                plugin.getApiProvider().getDefaultOptions(goshkow.addhead.api.HeadRenderTarget.TAB)
        );
        if (head == null || head.isBlank()) {
            head = plugin.getApiProvider().getFormattedHead(
                    targetId,
                    targetName,
                    goshkow.addhead.api.HeadFormat.TEXTURE_TAG,
                    plugin.getApiProvider().getDefaultOptions(goshkow.addhead.api.HeadRenderTarget.TAB)
            );
        }
        if (head == null || head.isBlank()) {
            head = plugin.getApiProvider().getFormattedHead(
                    targetId,
                    targetName,
                    goshkow.addhead.api.HeadFormat.ID_TAG,
                    plugin.getApiProvider().getDefaultOptions(goshkow.addhead.api.HeadRenderTarget.TAB)
            );
        }
        if (head == null || head.isBlank()) {
            head = plugin.getApiProvider().getFormattedHead(
                    targetId,
                    targetName,
                    goshkow.addhead.api.HeadFormat.NAME_TAG,
                    plugin.getApiProvider().getDefaultOptions(goshkow.addhead.api.HeadRenderTarget.TAB)
            );
        }
        if (head == null || head.isBlank()) {
            return "";
        }
        return head + Utils.buildTabHeadSeparatorMarkup(plugin.getTabHeadSpacing());
    }

    private UUID resolveTabPlayerUuid(Object tabPlayer) {
        Object uuid = invoke(tabPlayer, "getUniqueId");
        return uuid instanceof UUID id ? id : null;
    }

    private String resolveTabPlayerName(Object tabPlayer) {
        Object name = invoke(tabPlayer, "getName");
        return name instanceof String text ? text : null;
    }

    private void setStringValue(Object target, String methodName, Object tabPlayer, String value) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 2) {
                    continue;
                }
                method.invoke(target, tabPlayer, value);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Best effort only.
        }
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName, Class<?> parameterType, Object argument) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, argument);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String invokeString(Object target, String methodName, Object argument) {
        if (target == null || argument == null) {
            return null;
        }
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                Object value = method.invoke(target, argument);
                return value instanceof String text ? text : null;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private String stripOwnPlaceholder(String prefix) {
        if (prefix == null) {
            return "";
        }
        if (prefix.startsWith(RELATIONAL_PLACEHOLDER)) {
            return prefix.substring(RELATIONAL_PLACEHOLDER.length());
        }
        return prefix;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
