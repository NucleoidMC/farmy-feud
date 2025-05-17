package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import xyz.nucleoid.farmyfeud.FarmyFeud;
import xyz.nucleoid.farmyfeud.entity.Carriable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class EntityCarryStack<T extends Entity & Carriable> {
    private static final Identifier SLOW_MODIFIER_ID = Identifier.of(FarmyFeud.ID, "stack_slowness");

    private final int maximumWeight;

    private final List<T> stack = new ArrayList<>();
    private int weight;

    public EntityCarryStack(int maximumWeight) {
        this.maximumWeight = maximumWeight;
    }

    public boolean tryAdd(ServerPlayerEntity player, T entity) {
        if (this.weight + entity.getCarryWeight() > this.maximumWeight) {
            return false;
        }

        if (this.stack.contains(entity)) {
            return false;
        }

        Entity tail = player;
        if (!this.stack.isEmpty()) {
            tail = this.stack.get(this.stack.size() - 1);
        }

        entity.startRiding(tail, true);
        this.stack.add(entity);

        this.onStackChange(player);

        return true;
    }

    public List<T> dropAll(ServerPlayerEntity player) {
        if (this.stack.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> stack = new ArrayList<>(this.stack);
        this.stack.clear();

        for (T entity : stack) {
            entity.stopRiding();
        }

        this.onStackChange(player);

        return stack;
    }

    private void onStackChange(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(player));

        EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(SLOW_MODIFIER_ID);

        this.weight = 0;

        for (T entity : this.stack) {
            this.weight += entity.getCarryWeight();
        }

        if (this.weight > 0) {
            double baseValue = attribute.getBaseValue();
            double targetValue = baseValue / (this.getWeight() * 0.3 + 1);

            EntityAttributeModifier modifier = new EntityAttributeModifier(
                    SLOW_MODIFIER_ID,
                    targetValue - baseValue, EntityAttributeModifier.Operation.ADD_VALUE
            );

            attribute.addTemporaryModifier(modifier);
        }
    }

    public int getWeight() {
        return this.weight;
    }

    public boolean isEmpty() {
        return this.stack.isEmpty();
    }
}
