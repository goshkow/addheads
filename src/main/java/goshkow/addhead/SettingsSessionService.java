package goshkow.addhead;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks players waiting for a chat-based settings input.
 */
public final class SettingsSessionService {

    private final ConcurrentMap<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public void begin(UUID playerId, PendingInput input) {
        if (playerId != null && input != null) {
            pendingInputs.put(playerId, input);
        }
    }

    public PendingInput consume(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return pendingInputs.remove(playerId);
    }

    public boolean isPending(UUID playerId) {
        return playerId != null && pendingInputs.containsKey(playerId);
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            pendingInputs.remove(playerId);
        }
    }

    public enum PendingInput {
        SKIN_REFRESH_SECONDS
    }
}
