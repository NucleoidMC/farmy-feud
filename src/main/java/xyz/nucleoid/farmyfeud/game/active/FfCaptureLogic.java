package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.farmyfeud.entity.FarmSheepEntity;
import xyz.nucleoid.farmyfeud.game.map.FfMap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.game.common.team.GameTeamKey;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

record FfCaptureLogic(FfActive game) {

    @Nullable
    public GameTeamKey getTeamAt(Vec3d pos) {
        for (GameTeam team : this.game.config.teams()) {
            if (this.isCapturedBy(pos, team.key())) {
                return team.key();
            }
        }

        return null;
    }

    public void captureSheep(FarmSheepEntity sheep, GameTeamKey team) {
        if (team == sheep.getOwnerTeam()) {
            return;
        }

        if (sheep.getOwnerTeam() != null) {
            FfTeamState lastTeam = this.game.getTeam(sheep.getOwnerTeam());
            lastTeam.decrementCapturedSheep();
        }

        FfTeamState newTeam = this.game.getTeam(team);
        newTeam.incrementCapturedSheep();

        FfMap.TeamRegions regions = this.game.map.getTeamRegions(team);

        BlockBounds teamPen = regions != null ? regions.pen() : null;
        if (teamPen == null) {
            teamPen = BlockBounds.of(sheep.getBlockPos(), sheep.getBlockPos());
        }

        sheep.setOwnerTeam(team, teamPen);

        this.spawnFireworkAt(sheep, team);
    }

    private void spawnFireworkAt(Entity entity, GameTeamKey team) {
        World world = entity.getWorld();

        int flight = world.random.nextInt(3);

        FireworkRocketItem.Type type = world.random.nextInt(4) == 0 ? FireworkRocketItem.Type.STAR : FireworkRocketItem.Type.BURST;
        FireworkRocketEntity firework = new FireworkRocketEntity(
                world,
                entity.getX(),
                entity.getEyeY(),
                entity.getZ(),
                ItemStackBuilder.firework(this.game.teamManager.getTeamConfig(team).dyeColor().getRgb(), flight, type).build()
        );

        world.spawnEntity(firework);
    }

    private boolean isCapturedBy(Vec3d pos, GameTeamKey team) {
        FfMap.TeamRegions regions = this.game.map.getTeamRegions(team);
        if (regions == null || regions.pen() == null) {
            return false;
        }

        BlockPos min = regions.pen().min();
        BlockPos max = regions.pen().max();

        return pos.x >= min.getX() && pos.z >= min.getZ()
                && pos.x <= max.getX() && pos.z <= max.getZ();
    }
}
