package xyz.nucleoid.farmyfeud.entity;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xyz.nucleoid.farmyfeud.FarmyFeud;
import xyz.nucleoid.farmyfeud.game.active.FfActive;

public final class MegaFarmSheepEntity extends FarmSheepEntity {
    private static final Identifier MEGA_MODIFIER_ID = Identifier.of(FarmyFeud.ID, "mega");

    private EntityAttributeModifier SCALE_MODIFIER = new EntityAttributeModifier(MEGA_MODIFIER_ID, 0.65, EntityAttributeModifier.Operation.ADD_VALUE);
    private EntityAttributeModifier MOVEMENT_SPEED_MODIFIER = new EntityAttributeModifier(MEGA_MODIFIER_ID, -0.2, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    public MegaFarmSheepEntity(World world, FfActive game) {
        super(world, game);

        this.getAttributeInstance(EntityAttributes.SCALE).addPersistentModifier(SCALE_MODIFIER);
        this.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).addPersistentModifier(MOVEMENT_SPEED_MODIFIER);
    }

    @Override
    public int getCarryWeight() {
        return 3;
    }

    @Override
    public float getSoundPitch() {
        return (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 0.8F;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected Text getDefaultName() {
        return Text.translatable("entity.farmyfeud.mega_sheep");
    }
}
