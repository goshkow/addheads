package goshkow.addhead;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Viewer-based tab head injection through ProtocolLib.
 *
 * <p>This path lets AddHeads hide or show tab heads per viewer instead of
 * mutating the player list name globally for everyone.</p>
 */
public final class ProtocolLibTabViewHook {

    private final AddHeads plugin;
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Component>> viewerBaseNames = new ConcurrentHashMap<>();
    private PacketType playerInfoUpdateType;
    private PacketType playerInfoType;
    private ProtocolManager protocolManager;
    private PacketAdapter packetAdapter;

    public ProtocolLibTabViewHook(AddHeads plugin) {
        this.plugin = plugin;
    }

    public boolean start() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().warning("ProtocolLib not found. Falling back to global player-list head rendering.");
            return false;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();
        playerInfoUpdateType = resolvePacketType("PLAYER_INFO_UPDATE");
        playerInfoType = resolvePacketType("PLAYER_INFO");
        if (playerInfoUpdateType == null && playerInfoType == null) {
            plugin.getLogger().warning("ProtocolLib found, but no supported player info packet type was resolved. Falling back to global player-list head rendering.");
            return false;
        }

        List<PacketType> packetTypes = new ArrayList<>(2);
        if (playerInfoUpdateType != null) {
            packetTypes.add(playerInfoUpdateType);
        }
        if (playerInfoType != null && !packetTypes.contains(playerInfoType)) {
            packetTypes.add(playerInfoType);
        }
        packetAdapter = new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                packetTypes.toArray(PacketType[]::new)
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.isCancelled()) {
                    return;
                }
                rewritePacketForViewer(event);
            }
        };
        protocolManager.addPacketListener(packetAdapter);
        plugin.getLogger().info("ProtocolLib detected. Viewer-based tab head rendering is enabled.");
        return true;
    }

    public void stop() {
        if (protocolManager != null && packetAdapter != null) {
            protocolManager.removePacketListener(packetAdapter);
        }
        packetAdapter = null;
        protocolManager = null;
        viewerBaseNames.clear();
    }

    public void forgetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        viewerBaseNames.remove(playerId);
        for (Map<UUID, Component> entries : viewerBaseNames.values()) {
            entries.remove(playerId);
        }
    }

    public void refreshAllViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            refreshViewer(viewer);
        }
    }

    public void refreshViewer(Player viewer) {
        if (viewer == null || protocolManager == null) {
            return;
        }

        List<PlayerInfoData> entries = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            PlayerInfoData entry = buildEntryForViewer(viewer, target);
            if (entry != null) {
                entries.add(entry);
            }
        }
        sendDisplayNameUpdate(viewer, entries);
    }

    public void refreshEntryForAllViewers(Player target) {
        if (target == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerInfoData entry = buildEntryForViewer(viewer, target);
            if (entry == null) {
                continue;
            }
            sendDisplayNameUpdate(viewer, List.of(entry));
        }
    }

    private void rewritePacketForViewer(PacketEvent event) {
        if (event == null || event.isReadOnly()) {
            return;
        }
        if (plugin.getTabApiBridge() != null && plugin.getTabApiBridge().isManagingTabFormatting()) {
            return;
        }
        Player viewer = event.getPlayer();
        PacketContainer packet = event.getPacket().deepClone();
        if (!shouldRewritePacket(packet)) {
            return;
        }
        List<PlayerInfoData> original = readPlayerInfoDataList(packet);
        if (original == null || original.isEmpty()) {
            return;
        }

        List<PlayerInfoData> rewritten = new ArrayList<>(original.size());
        boolean changed = false;
        for (PlayerInfoData entry : original) {
            if (entry == null) {
                rewritten.add(null);
                continue;
            }
            PlayerInfoData updated = rewriteEntryForViewer(viewer, entry, false);
            rewritten.add(updated);
            if (updated != entry) {
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        writePlayerInfoDataList(packet, rewritten);
        event.setPacket(packet);
    }

    private PlayerInfoData rewriteEntryForViewer(Player viewer, PlayerInfoData entry, boolean forceExplicitName) {
        if (entry == null) {
            return null;
        }
        if (plugin.getTabApiBridge() != null && plugin.getTabApiBridge().isManagingTabFormatting()) {
            return entry;
        }
        if (!plugin.shouldRenderTabHeadFor(viewer) && !forceExplicitName) {
            return entry;
        }

        UUID targetId = entry.getProfileId();
        WrappedGameProfile profile = entry.getProfile();
        if (targetId == null && profile != null) {
            targetId = profile.getUUID();
        }
        String targetName = resolveProfileName(profile, targetId);

        Component base = resolveBaseComponent(viewer, targetId, targetName, entry.getDisplayName());
        rememberBaseComponent(viewer, targetId, base);

        Component display = plugin.shouldRenderTabHeadFor(viewer)
                ? Utils.prependTabHead(
                plugin.getSkinService(),
                base,
                targetId,
                targetName,
                plugin.getTabHeadSpacing(),
                plugin.isTabHeadShadowEnabled()
        )
                : base;

        WrappedChatComponent wrappedDisplay = WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(display));
        return new PlayerInfoData(
                entry.getProfileId(),
                entry.getLatency(),
                entry.isListed(),
                entry.getGameMode(),
                entry.getProfile(),
                wrappedDisplay,
                entry.isShowHat(),
                entry.getListOrder(),
                entry.getRemoteChatSessionData()
        );
    }

    private PlayerInfoData buildEntryForViewer(Player viewer, Player target) {
        if (viewer == null || target == null) {
            return null;
        }
        if (plugin.getTabApiBridge() != null && plugin.getTabApiBridge().isManagingTabFormatting()) {
            return null;
        }

        WrappedGameProfile profile = WrappedGameProfile.fromPlayer(target);
        Component base = resolveBaseComponent(viewer, target.getUniqueId(), target.getName(), null);
        rememberBaseComponent(viewer, target.getUniqueId(), base);
        Component display = plugin.shouldRenderTabHeadFor(viewer)
                ? Utils.prependTabHead(
                plugin.getSkinService(),
                base,
                target.getUniqueId(),
                target.getName(),
                plugin.getTabHeadSpacing(),
                plugin.isTabHeadShadowEnabled()
        )
                : base;

        WrappedChatComponent wrappedDisplay = WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(display));
        return new PlayerInfoData(
                target.getUniqueId(),
                target.getPing(),
                true,
                EnumWrappers.NativeGameMode.fromBukkit(target.getGameMode()),
                profile,
                wrappedDisplay,
                true,
                0,
                null
        );
    }

    private void sendDisplayNameUpdate(Player viewer, List<PlayerInfoData> entries) {
        if (viewer == null || entries == null || entries.isEmpty() || protocolManager == null) {
            return;
        }

        PacketContainer packet;
        if (playerInfoUpdateType != null) {
            packet = protocolManager.createPacket(playerInfoUpdateType);
            packet.getModifier().writeDefaults();
            packet.getPlayerInfoActions().writeSafely(0, EnumSet.of(
                    EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
                    EnumWrappers.PlayerInfoAction.UPDATE_LISTED
            ));
        } else if (playerInfoType != null) {
            packet = protocolManager.createPacket(playerInfoType);
            packet.getModifier().writeDefaults();
            packet.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
            packet.getPlayerInfoActions().writeSafely(0, EnumSet.of(
                    EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
                    EnumWrappers.PlayerInfoAction.UPDATE_LISTED
            ));
        } else {
            return;
        }

        writePlayerInfoDataList(packet, entries);
        try {
            protocolManager.sendServerPacket(viewer, packet, false);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to refresh viewer-specific tab entry for " + viewer.getName() + ": " + exception.getMessage());
        }
    }

    private Component resolveBaseComponent(Player target) {
        Component current = target.playerListName();
        if (current != null) {
            return current;
        }
        return Component.text(target.getName());
    }

    private String resolveProfileName(WrappedGameProfile profile, UUID targetId) {
        if (profile != null) {
            try {
                String name = profile.getName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } catch (RuntimeException ignored) {
                // Some ProtocolLib / profile variants do not expose the name through this wrapper.
            }
        }

        Player online = targetId != null ? Bukkit.getPlayer(targetId) : null;
        if (online != null) {
            return online.getName();
        }
        return Utils.resolveName(targetId, null);
    }

    private Component resolveBaseComponent(Player viewer, UUID targetId, String targetName, WrappedChatComponent packetDisplayName) {
        Component base = deserialize(packetDisplayName);
        Component tabApiBase = plugin.getTabApiBridge() != null
                ? plugin.getTabApiBridge().resolveBaseComponent(targetId, targetName)
                : null;
        if (tabApiBase != null && (base == null || isPlainNameComponent(base, targetId, targetName))) {
            return tabApiBase;
        }
        if (base != null) {
            return base;
        }
        if (tabApiBase != null) {
            return tabApiBase;
        }

        Player online = targetId != null ? Bukkit.getPlayer(targetId) : null;
        if (online != null) {
            Component teamFormatted = resolveTeamFormattedBase(viewer, online);
            if (teamFormatted != null) {
                return teamFormatted;
            }
            return getCachedBaseComponent(viewer, online);
        }
        return getCachedBaseComponent(viewer, targetId, targetName);
    }

    private Component getCachedBaseComponent(Player viewer, Player target) {
        if (viewer == null || target == null) {
            return target != null ? resolveBaseComponent(target) : Component.empty();
        }
        Map<UUID, Component> entries = viewerBaseNames.get(viewer.getUniqueId());
        if (entries != null) {
            Component cached = entries.get(target.getUniqueId());
            if (cached != null) {
                return cached;
            }
        }
        return resolveBaseComponent(target);
    }

    private Component getCachedBaseComponent(Player viewer, UUID targetId, String targetName) {
        if (viewer == null || targetId == null) {
            return Component.text(Utils.resolveName(targetId, targetName));
        }
        Map<UUID, Component> entries = viewerBaseNames.get(viewer.getUniqueId());
        if (entries != null) {
            Component cached = entries.get(targetId);
            if (cached != null) {
                return cached;
            }
        }
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            return resolveBaseComponent(online);
        }
        return Component.text(Utils.resolveName(targetId, targetName));
    }

    private Component resolveTeamFormattedBase(Player viewer, Player target) {
        if (viewer == null || target == null) {
            return null;
        }

        Team team = viewer.getScoreboard().getEntryTeam(target.getName());
        if (team == null) {
            return null;
        }

        TextComponent.Builder builder = Component.text();
        String prefix = team.getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            builder.append(LegacyComponentSerializer.legacySection().deserialize(prefix));
        }

        Component nameComponent = Component.text(target.getName());
        ChatColor color = team.getColor();
        if (color != null && color != ChatColor.RESET) {
            NamedTextColor named = toNamedTextColor(color);
            if (named != null) {
                nameComponent = nameComponent.color(named);
            }
        }
        builder.append(nameComponent);

        String suffix = team.getSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            builder.append(LegacyComponentSerializer.legacySection().deserialize(suffix));
        }

        return builder.build();
    }

    private NamedTextColor toNamedTextColor(ChatColor color) {
        return switch (color) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    private void rememberBaseComponent(Player viewer, UUID targetId, Component base) {
        if (viewer == null || targetId == null || base == null) {
            return;
        }
        viewerBaseNames
                .computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(targetId, base);
    }

    private Component deserialize(WrappedChatComponent component) {
        if (component == null) {
            return null;
        }
        try {
            return GsonComponentSerializer.gson().deserialize(component.getJson());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isPlainNameComponent(Component base, UUID targetId, String targetName) {
        String resolvedName = Utils.resolveName(targetId, targetName);
        if (base instanceof TextComponent textComponent) {
            return textComponent.children().isEmpty() && resolvedName.equals(textComponent.content());
        }
        return Component.text(resolvedName).equals(base);
    }

    private List<PlayerInfoData> readPlayerInfoDataList(PacketContainer packet) {
        StructureModifier<List<PlayerInfoData>> modifier = playerInfoDataListModifier(packet);
        if (modifier.size() <= 0) {
            return null;
        }
        int index = resolvePlayerInfoDataIndex(packet, modifier.size());
        return index >= 0 ? modifier.readSafely(index) : null;
    }

    private void writePlayerInfoDataList(PacketContainer packet, List<PlayerInfoData> data) {
        StructureModifier<List<PlayerInfoData>> modifier = playerInfoDataListModifier(packet);
        if (modifier.size() <= 0) {
            return;
        }
        int index = resolvePlayerInfoDataIndex(packet, modifier.size());
        if (index >= 0) {
            modifier.write(index, data);
        }
    }

    private boolean shouldRewritePacket(PacketContainer packet) {
        if (packet == null) {
            return false;
        }
        if (playerInfoUpdateType != null && packet.getType().equals(playerInfoUpdateType)) {
            try {
                Set<EnumWrappers.PlayerInfoAction> actions = packet.getPlayerInfoActions().readSafely(0);
                if (actions == null || actions.isEmpty()) {
                    return false;
                }
                return actions.contains(EnumWrappers.PlayerInfoAction.ADD_PLAYER)
                        || actions.contains(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
            } catch (RuntimeException ignored) {
                return true;
            }
        }
        return playerInfoType != null && packet.getType().equals(playerInfoType);
    }

    private StructureModifier<List<PlayerInfoData>> playerInfoDataListModifier(PacketContainer packet) {
        return packet.getModifier().withType(
                List.class,
                BukkitConverters.getListConverter(PlayerInfoData.getConverter())
        );
    }

    private int resolvePlayerInfoDataIndex(PacketContainer packet, int size) {
        if (size <= 0) {
            return -1;
        }
        if (playerInfoUpdateType != null && packet.getType().equals(playerInfoUpdateType)) {
            return Math.min(1, size - 1);
        }
        return 0;
    }

    private PacketType resolvePacketType(String fieldName) {
        try {
            Field field = PacketType.Play.Server.class.getField(fieldName);
            Object value = field.get(null);
            return value instanceof PacketType packetType ? packetType : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
