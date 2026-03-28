package goshkow.addhead.api.event;

import goshkow.addhead.api.SkinTexture;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired when AddHeads resolves or refreshes a player's texture payload.
 */
public final class AddHeadsSkinResolvedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final SkinTexture texture;

    public AddHeadsSkinResolvedEvent(UUID playerId, String playerName, SkinTexture texture) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.texture = texture;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public SkinTexture getTexture() {
        return texture;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
