package xyz.nucleoid.farmyfeud.entity;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import xyz.nucleoid.farmyfeud.game.active.FfActive;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamKey;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class FarmSheepEntity extends Sheep {
    private final FfActive game;
    private GameTeamKey ownerTeam;
    private BlockBounds home;

    private Vec3 lastDropPos;

    private long lastPickUpTime;
    private GameTeamKey lastPickUpTeam;

    private long lastValidTime;

    public FarmSheepEntity(Level world, FfActive game) {
        super(EntityType.SHEEP, world);
        this.game = game;
        this.lastValidTime = world.getGameTime();
    }

    public void setOwnerTeam(@Nullable GameTeamKey team, BlockBounds home) {
        this.ownerTeam = team;
        this.home = home;
        this.lastDropPos = null;
        this.lastValidTime = this.level().getGameTime();

        this.getNavigation().stop();

        if (team != null) {
            this.setCustomName(null);
            this.setColor(this.game.teamManager.getTeamConfig(team).blockDyeColor());
        } else {
            this.setCustomName(Component.literal("jeb_"));
            this.setCustomNameVisible(false);
            this.setColor(DyeColor.WHITE);
        }
    }

    public void drop() {
        this.lastDropPos = this.position();
        this.lastPickUpTime = this.level().getGameTime();
    }

    public boolean tryPickUp(long time, GameTeamKey team) {
        if (time - this.lastPickUpTime >= FfActive.PICK_UP_INTERVAL_TICKS) {
            this.lastPickUpTime = time;
            this.lastPickUpTeam = team;
            return true;
        }
        return false;
    }

    public void teleportWithPoof(double x, double y, double z) {
        if (this.level() instanceof ServerLevel world) {
            var colorComponents = this.getColor().getTextureDiffuseColor();
            var particle = new DustParticleOptions(colorComponents, 2f);

            for (int i = 0; i < 20; i++) {
                double deltaX = this.random.nextGaussian() * 0.02;
                double deltaY = this.random.nextGaussian() * 0.02;
                double deltaZ = this.random.nextGaussian() * 0.02;

                world.sendParticles(particle, this.getRandomX(1), this.getRandomY(), this.getRandomZ(1), 1, deltaX, deltaY, deltaZ, 0.1);
            }
        }

        this.randomTeleport(x, y, z, false);
    }

    @Nullable
    public BlockBounds getHome() {
        return this.home;
    }

    @Nullable
    public Vec3 getLastDropPos() {
        return this.lastDropPos;
    }

    @Nullable
    public GameTeamKey getLastPickUpTeam() {
        return this.lastPickUpTeam;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WanderToHomeGoal());
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel world) {
        this.game.tickSheep(this);

        if (this.home != null) {
            long time = this.level().getGameTime();
            if (this.isPassenger() || this.home.contains(this.blockPosition())) {
                this.lastValidTime = time;
            }

            if (time - this.lastValidTime > 20 * 20) {
                Vec3 center = this.home.center();
                this.teleportWithPoof(center.x(), center.y() + 0.5, center.z());
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        Entity vehicle = this.getVehicle();
        if (vehicle != null && source.getDirectEntity() != null) {
            return vehicle.hurtServer(world, source, amount);
        }

        return super.hurtServer(world, source, amount);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Nullable
    public GameTeamKey getOwnerTeam() {
        return this.ownerTeam;
    }

    private class WanderToHomeGoal extends Goal {
        public WanderToHomeGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return FarmSheepEntity.this.home != null && FarmSheepEntity.this.random.nextInt(20) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !FarmSheepEntity.this.getNavigation().isDone();
        }

        @Override
        public void start() {
            Vec3 target = this.getWanderTarget();
            FarmSheepEntity.this.getNavigation().moveTo(target.x(), target.y(), target.z(), 0.8);
        }

        private Vec3 getWanderTarget() {
            BlockBounds home = FarmSheepEntity.this.home;
            BlockPos homeMin = home.min();
            BlockPos homeMax = home.max();

            RandomSource random = FarmSheepEntity.this.random;

            double x = FarmSheepEntity.this.getX();
            double z = FarmSheepEntity.this.getZ();

            int targetX = random.nextInt(homeMax.getX() - homeMin.getX() + 1) + homeMin.getX();
            int targetZ = random.nextInt(homeMax.getZ() - homeMin.getZ() + 1) + homeMin.getZ();

            double deltaX = x - targetX;
            double deltaZ = z - targetZ;

            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (distance > 16.0) {
                deltaX = deltaX / distance * 16.0;
                deltaZ = deltaZ / distance * 16.0;

                targetX = Mth.floor(x + deltaX);
                targetZ = Mth.floor(z + deltaZ);
            }

            int topY = FarmSheepEntity.this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
            return new Vec3(targetX + 0.5, topY, targetZ + 0.5);
        }

        @Override
        public void stop() {
            FarmSheepEntity.this.getNavigation().stop();
        }
    }
}
