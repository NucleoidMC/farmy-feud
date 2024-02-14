package xyz.nucleoid.farmyfeud.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import xyz.nucleoid.farmyfeud.game.active.FfActive;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.common.team.GameTeamKey;

import java.util.EnumSet;

public final class FarmSheepEntity extends SheepEntity {
    private final FfActive game;
    private GameTeamKey ownerTeam;
    private BlockBounds home;

    private Vec3d lastDropPos;

    private long lastPickUpTime;
    private GameTeamKey lastPickUpTeam;

    private long lastValidTime;

    public FarmSheepEntity(World world, FfActive game) {
        super(EntityType.SHEEP, world);
        this.game = game;
        this.lastValidTime = world.getTime();
    }

    public void setOwnerTeam(@Nullable GameTeamKey team, BlockBounds home) {
        this.ownerTeam = team;
        this.home = home;
        this.lastDropPos = null;
        this.lastValidTime = this.getWorld().getTime();

        this.getNavigation().stop();

        if (team != null) {
            this.setCustomName(null);
            this.setColor(this.game.teamManager.getTeamConfig(team).blockDyeColor());
        } else {
            this.setCustomName(Text.literal("jeb_"));
            this.setCustomNameVisible(false);
            this.setColor(DyeColor.WHITE);
        }
    }

    public void drop() {
        this.lastDropPos = this.getPos();
        this.lastPickUpTime = this.getWorld().getTime();
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
        if (this.getWorld() instanceof ServerWorld world) {
            var colorComponents = this.getColor().getColorComponents();
            var particle = new DustParticleEffect(new Vector3f(colorComponents), 2f);

            for (int i = 0; i < 20; i++) {
                double deltaX = this.random.nextGaussian() * 0.02;
                double deltaY = this.random.nextGaussian() * 0.02;
                double deltaZ = this.random.nextGaussian() * 0.02;

                world.spawnParticles(particle, this.getParticleX(1), this.getRandomBodyY(), this.getParticleZ(1), 1, deltaX, deltaY, deltaZ, 0.1);
            }
        }

        this.teleport(x, y, z);
    }

    @Nullable
    public BlockBounds getHome() {
        return this.home;
    }

    @Nullable
    public Vec3d getLastDropPos() {
        return this.lastDropPos;
    }

    @Nullable
    public GameTeamKey getLastPickUpTeam() {
        return this.lastPickUpTeam;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new WanderToHomeGoal());
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.add(3, new LookAroundGoal(this));
    }

    @Override
    protected void mobTick() {
        this.game.tickSheep(this);

        if (this.home != null) {
            long time = this.getWorld().getTime();
            if (this.hasVehicle() || this.home.contains(this.getBlockPos())) {
                this.lastValidTime = time;
            }

            if (time - this.lastValidTime > 20 * 20) {
                Vec3d center = this.home.center();
                this.teleportWithPoof(center.getX(), center.getY() + 0.5, center.getZ());
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        Entity vehicle = this.getVehicle();
        if (vehicle != null && source.getSource() != null) {
            return vehicle.damage(source, amount);
        }

        return super.damage(source, amount);
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        return ActionResult.PASS;
    }

    @Nullable
    public GameTeamKey getOwnerTeam() {
        return this.ownerTeam;
    }

    private class WanderToHomeGoal extends Goal {
        public WanderToHomeGoal() {
            this.setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return FarmSheepEntity.this.home != null && FarmSheepEntity.this.random.nextInt(20) == 0;
        }

        @Override
        public boolean shouldContinue() {
            return !FarmSheepEntity.this.getNavigation().isIdle();
        }

        @Override
        public void start() {
            Vec3d target = this.getWanderTarget();
            FarmSheepEntity.this.getNavigation().startMovingTo(target.getX(), target.getY(), target.getZ(), 0.8);
        }

        private Vec3d getWanderTarget() {
            BlockBounds home = FarmSheepEntity.this.home;
            BlockPos homeMin = home.min();
            BlockPos homeMax = home.max();

            Random random = FarmSheepEntity.this.random;

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

                targetX = MathHelper.floor(x + deltaX);
                targetZ = MathHelper.floor(z + deltaZ);
            }

            int topY = FarmSheepEntity.this.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, targetX, targetZ);
            return new Vec3d(targetX + 0.5, topY, targetZ + 0.5);
        }

        @Override
        public void stop() {
            FarmSheepEntity.this.getNavigation().stop();
        }
    }
}
