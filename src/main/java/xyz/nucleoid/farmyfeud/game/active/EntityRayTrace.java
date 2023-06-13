package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

public final class EntityRayTrace {
    @Nullable
    public static EntityHitResult rayTrace(Entity sourceEntity, double range, double margin, Predicate<Entity> predicate) {
        World world = sourceEntity.getWorld();

        Vec3d origin = sourceEntity.getCameraPosVec(1.0F);
        Vec3d delta = sourceEntity.getRotationVec(1.0F).multiply(range);

        Vec3d target = origin.add(delta);

        double testMargin = Math.max(1.0, margin);

        Box testBox = sourceEntity.getBoundingBox()
                .stretch(delta)
                .expand(testMargin, testMargin, testMargin);

        double minDistance2 = range * range;
        Entity hitEntity = null;
        Vec3d hitPoint = null;

        for (Entity entity : world.getOtherEntities(sourceEntity, testBox, predicate)) {
            Box targetBox = entity.getBoundingBox().expand(Math.max(entity.getTargetingMargin(), margin));

            Optional<Vec3d> traceResult = targetBox.raycast(origin, target);
            if (targetBox.contains(origin)) {
                return new EntityHitResult(entity, traceResult.orElse(origin));
            }

            if (traceResult.isPresent()) {
                Vec3d tracePoint = traceResult.get();
                double distance2 = origin.squaredDistanceTo(tracePoint);

                if (distance2 < minDistance2) {
                    hitEntity = entity;
                    hitPoint = tracePoint;
                    minDistance2 = distance2;
                }
            }
        }

        if (hitEntity == null) {
            return null;
        }

        return new EntityHitResult(hitEntity, hitPoint);
    }
}
