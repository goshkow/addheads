package goshkow.addhead;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that AddHeads never performs skin resolution (which blocks on
 * SkinsRestorer/Mojang HTTPS lookups) on the main server thread.
 *
 * <p>Regression tests for the Paper watchdog freeze: the refresh cycle used to
 * be scheduled with {@code scheduleSyncRepeatingTask}, causing SkinsRestorer's
 * {@code getSkinForPlayer()} to perform synchronous Mojang HTTPS fetches on the
 * main thread.</p>
 */
class SkinServiceThreadingTest {

    private MockedStatic<Bukkit> bukkit;
    private BukkitScheduler scheduler;
    private AddHeads plugin;
    private SkinService skinService;

    @BeforeEach
    void setUp() {
        scheduler = mock(BukkitScheduler.class);
        plugin = mock(AddHeads.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        // SkinsRestorer absent: its property lookup short-circuits to an empty list.
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        // Unknown offline players make Utils.resolveName fall back to the UUID string.
        OfflinePlayer unknownOfflinePlayer = mock(OfflinePlayer.class);
        bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenReturn(unknownOfflinePlayer);
        skinService = new SkinService(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private BukkitTask stubAsyncTask() {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(42);
        return task;
    }

    @Test
    void startSchedulesRefreshCycleAsynchronously() {
        BukkitTask task = stubAsyncTask();
        when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);

        skinService.start(6000L);

        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(6000L), eq(6000L));
        verify(scheduler, never()).scheduleSyncRepeatingTask(any(), any(Runnable.class), anyLong(), anyLong());
        // Immediate initial pass is off the main thread too.
        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }

    @Test
    void asyncCycleDispatchesTabRefreshBackToMainThread() {
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        BukkitTask task = stubAsyncTask();
        when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);

        skinService.start(6000L);

        ArgumentCaptor<Runnable> cycleCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), cycleCaptor.capture(), eq(6000L), eq(6000L));

        cycleCaptor.getValue().run();

        ArgumentCaptor<Runnable> syncCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), syncCaptor.capture());
        syncCaptor.getValue().run();
        verify(plugin).refreshAllTabListNames();
    }

    @Test
    void refreshOnMainThreadIsDelegatedToAsyncScheduler() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

        skinService.refresh(UUID.randomUUID(), "Notch");

        // No synchronous resolution: the work is queued for the async scheduler.
        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }

    @Test
    void refreshOffMainThreadResolvesInline() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());

        skinService.refresh(UUID.randomUUID(), null);

        // No main-thread hop for already-async callers.
        verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }

    @Test
    void getTextureOnMainThreadWithColdCacheReturnsNullAndWarmsAsync() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

        Utils.TextureData result = skinService.getTexture(UUID.randomUUID(), "Notch");

        assertNull(result);
        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }

    @Test
    void refreshCycleWithOnlinePlayersStillRunsOffMainThread() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        UUID playerId = UUID.randomUUID();

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        // Unknown name: resolution yields nothing, so no follow-up Paper profile
        // or Mojang HTTP lookups are triggered either.
        when(player.getName()).thenReturn(null);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));

        skinService.refreshOnlinePlayers();

        // The only main-thread hop is the tab-list refresh after the cycle.
        verify(scheduler).runTask(eq(plugin), any(Runnable.class));
        // Resolving an unknown player must not queue additional async work.
        verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }
}