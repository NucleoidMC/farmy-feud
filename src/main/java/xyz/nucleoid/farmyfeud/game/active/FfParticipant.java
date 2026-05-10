package xyz.nucleoid.farmyfeud.game.active;

import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.farmyfeud.entity.FarmSheepEntity;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamKey;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class FfParticipant {
    private final GameSpace gameSpace;
    public final UUID playerId;
    public final GameTeamKey team;

    public final EntityCarryStack<FarmSheepEntity> carryStack = new EntityCarryStack<>(3);

    private long respawnTime = -1;

    FfParticipant(GameSpace gameSpace, ServerPlayer player, GameTeamKey team) {
        this.gameSpace = gameSpace;
        this.playerId = player.getUUID();
        this.team = team;
    }

    void startRespawn(long time) {
        this.respawnTime = time + FfActive.RESPAWN_TICKS;
    }

    boolean tryRespawn(long time) {
        if (this.respawnTime != -1 && time >= this.respawnTime) {
            this.respawnTime = -1;
            return true;
        }
        return false;
    }

    @Nullable
    public ServerPlayer player() {
        return this.gameSpace.getPlayers().getEntity(this.playerId);
    }
}
