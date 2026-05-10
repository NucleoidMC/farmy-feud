package xyz.nucleoid.farmyfeud.game.active;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class EntityRayTrace {
    @Nullable
    public static EntityHitResult rayTrace(Entity sourceEntity, double range, double margin, Predicate<Entity> predicate) {
        Level world = sourceEntity.level();

        Vec3 origin = sourceEntity.getEyePosition(1.0F);
        Vec3 delta = sourceEntity.getViewVector(1.0F).scale(range);

        Vec3 target = origin.add(delta);

        double testMargin = Math.max(1.0, margin);

        AABB testBox = sourceEntity.getBoundingBox()
                .expandTowards(delta)
                .inflate(testMargin, testMargin, testMargin);

        double minDistance2 = range * range;
        Entity hitEntity = null;
        Vec3 hitPoint = null;

        for (Entity entity : world.getEntities(sourceEntity, testBox, predicate)) {
            AABB targetBox = entity.getBoundingBox().inflate(Math.max(entity.getPickRadius(), margin));

            Optional<Vec3> traceResult = targetBox.clip(origin, target);
            if (targetBox.contains(origin)) {
                return new EntityHitResult(entity, traceResult.orElse(origin));
            }

            if (traceResult.isPresent()) {
                Vec3 tracePoint = traceResult.get();
                double distance2 = origin.distanceToSqr(tracePoint);

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
