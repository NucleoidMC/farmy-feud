package xyz.nucleoid.farmyfeud.game;

import xyz.nucleoid.farmyfeud.game.map.FfMap;
import xyz.nucleoid.map_templates.BlockBounds;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.game.common.team.GameTeamKey;

public record FfSpawnLogic(ServerWorld world, FfMap map) {

    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.clearStatusEffects();
        player.setHealth(20.0F);
        player.getHungerManager().setFoodLevel(20);
        player.fallDistance = 0.0F;
        player.changeGameMode(gameMode);
        player.setFireTicks(0);
    }

    public void spawnPlayerAtCenter(ServerPlayerEntity player) {
        BlockBounds centerSpawn = this.map.getCenterSpawn();
        if (centerSpawn != null) {
            this.spawnAt(player, new BlockPos(centerSpawn.center()));
        } else {
            this.spawnAt(player, BlockPos.ORIGIN);
        }
    }

    public void spawnPlayerAtTeam(ServerPlayerEntity player, GameTeamKey team) {
        FfMap.TeamRegions teamRegions = this.map.getTeamRegions(team);
        if (teamRegions == null) {
            return;
        }

        BlockBounds teamSpawn = teamRegions.spawn();
        if (teamSpawn != null) {
            BlockPos spawnPos = new BlockPos(teamSpawn.center());
            this.spawnAt(player, spawnPos);
        }
    }

    private void spawnAt(ServerPlayerEntity player, BlockPos spawnPos) {
        player.teleport(this.world, spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5, 0.0F, 0.0F);
    }
}
