package xyz.nucleoid.farmyfeud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.farmyfeud.entity.FarmSheepEntity;

@Mixin(Entity.class)
public class EntityMixin {
    @WrapOperation(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
    private boolean ignoreRestrictionsForFarmSheep(EntityType<?> instance, Operation<Boolean> original) {
        if (((Object) this) instanceof FarmSheepEntity) {
            return true;
        }
        return original.call(instance);
    }
}
