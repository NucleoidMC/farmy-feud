package xyz.nucleoid.farmyfeud.game;

import xyz.nucleoid.farmyfeud.game.map.FfMap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamKey;

import java.util.Set;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public record FfSpawnLogic(ServerLevel world, FfMap map) {

    public void resetPlayer(ServerPlayer player, GameType gameMode) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        player.removeAllEffects();
        player.setHealth(20.0F);
        player.getFoodData().setFoodLevel(20);
        player.fallDistance = 0.0F;
        player.setGameMode(gameMode);
        player.setRemainingFireTicks(0);
    }

    public void spawnPlayerAtCenter(ServerPlayer player) {
        BlockBounds centerSpawn = this.map.getCenterSpawn();
        if (centerSpawn != null) {
            this.spawnAt(player, BlockPos.containing(centerSpawn.center()), 0);
        } else {
            this.spawnAt(player, BlockPos.ZERO, 0);
        }
    }

    public void spawnPlayerAtTeam(ServerPlayer player, GameTeamKey team) {
        FfMap.TeamRegions teamRegions = this.map.getTeamRegions(team);
        if (teamRegions == null) {
            return;
        }

        BlockBounds teamSpawn = teamRegions.spawn();
        if (teamSpawn != null) {
            BlockPos spawnPos = BlockPos.containing(teamSpawn.center());
            BlockPos center;
            var centerSpawn = this.map.getCenterSpawn();
            if (centerSpawn != null) {
                center = BlockPos.containing(centerSpawn.center());
            } else {
                center = BlockPos.ZERO;
            }

            this.spawnAt(player, spawnPos, 0);
            player.lookAt(EntityAnchorArgument.Anchor.FEET, Vec3.atCenterOf(center.atY(spawnPos.getY())));
        }
    }

    private void spawnAt(ServerPlayer player, BlockPos spawnPos, float yaw) {
        player.teleportTo(this.world, spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5, Set.of(), yaw, 0.0F, false);
    }
}
