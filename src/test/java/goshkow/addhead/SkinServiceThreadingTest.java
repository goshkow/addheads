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
 * Verifies that AddHeads never resolves skins (which blocks on
 * SkinsRestorer/Mojang HTTPS lookups) on a ticking thread, and that player-list
 * refreshes are dispatched back to the global thread through
 * {@link Schedulers}.
 *
 * <p>Regression tests for issue #9 (watchdog freeze caused by synchronous skin
 * resolution) and issue #7 (Folia rejects {@code BukkitScheduler}).</p>
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
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        // Unknown offline players make Utils.resolveName fall back to the UUID string.
        bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenReturn(mock(OfflinePlayer.class));
        skinService = new SkinService(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private BukkitTask stubAsyncTimer() {
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);
        return task;
    }

    @Test
    void startSchedulesRefreshCycleOffTheTickingThreads() {
        stubAsyncTimer();

        skinService.start(6000L);

        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(6000L), eq(6000L));
        verify(scheduler, never()).scheduleSyncRepeatingTask(any(), any(Runnable.class), anyLong(), anyLong());
        // The immediate first pass is off the ticking threads as well.
        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }

    @Test
    void stopCancelsTheRefreshCycle() {
        BukkitTask task = stubAsyncTimer();

        skinService.start(6000L);
        skinService.stop();

        verify(task).cancel();
    }

    @Test
    void refreshAlwaysDefersToTheAsyncScheduler() {
        skinService.refresh(UUID.randomUUID(), "Notch");

        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        // No work is queued on the global thread and nothing is resolved inline.
        verify(scheduler, never()).runTask(any(), any(Runnable.class));
        bukkit.verify(() -> Bukkit.getPluginManager(), never());
    }

    @Test
    void refreshLaterUsesADelayedAsyncTask() {
        skinService.refreshLater(UUID.randomUUID(), "Notch", 20L);

        verify(scheduler).runTaskLaterAsynchronously(eq(plugin), any(Runnable.class), eq(20L));
        verify(scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
    }

    @Test
    void getTextureWithColdCacheReturnsNullAndWarmsAsynchronously() {
        Utils.TextureData texture = skinService.getTexture(UUID.randomUUID(), "Notch");

        assertNull(texture);
        verify(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        bukkit.verify(() -> Bukkit.getPluginManager(), never());
    }

    @Test
    void refreshCycleDispatchesTabRefreshBackToTheGlobalThread() {
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        stubAsyncTimer();

        skinService.start(6000L);

        ArgumentCaptor<Runnable> cycleCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimerAsynchronously(eq(plugin), cycleCaptor.capture(), eq(6000L), eq(6000L));
        cycleCaptor.getValue().run();

        ArgumentCaptor<Runnable> globalCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), globalCaptor.capture());
        globalCaptor.getValue().run();
        verify(plugin).refreshAllTabListNames();
    }

    @Test
    void refreshCycleWithOnlinePlayersStillRunsOffTheTickingThreads() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        // Unknown name: resolution yields nothing, so no follow-up profile or
        // Mojang lookups are triggered either.
        when(player.getName()).thenReturn(null);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));

        skinService.refreshOnlinePlayers();

        // The only global-thread hop is the tab-list refresh after the cycle.
        verify(scheduler).runTask(eq(plugin), any(Runnable.class));
        // Resolving an unknown player must not queue additional async work.
        verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
    }
}
