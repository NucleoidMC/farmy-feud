package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.farmyfeud.entity.FarmSheepEntity;
import xyz.nucleoid.farmyfeud.game.map.FfMap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamKey;
import xyz.nucleoid.plasmid.api.util.ItemStackBuilder;

record FfCaptureLogic(FfActive game) {

    @Nullable
    public GameTeamKey getTeamAt(Vec3 pos) {
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
            teamPen = BlockBounds.of(sheep.blockPosition(), sheep.blockPosition());
        }

        sheep.setOwnerTeam(team, teamPen);

        this.spawnFireworkAt(sheep, team);
    }

    private void spawnFireworkAt(Entity entity, GameTeamKey team) {
        Level world = entity.level();

        int flight = world.getRandom().nextInt(3);

        var type = world.getRandom().nextInt(4) == 0 ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.BURST;
        FireworkRocketEntity firework = new FireworkRocketEntity(
                world,
                entity.getX(),
                entity.getEyeY(),
                entity.getZ(),
                ItemStackBuilder.firework(this.game.teamManager.getTeamConfig(team).dyeColor().getValue(), flight, type).build()
        );

        world.addFreshEntity(firework);
    }

    private boolean isCapturedBy(Vec3 pos, GameTeamKey team) {
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
