package xyz.nucleoid.farmyfeud.game.active;

import xyz.nucleoid.farmyfeud.FarmyFeud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class EntityCarryStack<T extends Entity> {
    private static final Identifier SLOW_MODIFIER_ID = Identifier.fromNamespaceAndPath(FarmyFeud.ID, "stack_slowness");

    private final int maximumHeight;
    private final List<T> stack = new ArrayList<>();

    public EntityCarryStack(int maximumHeight) {
        this.maximumHeight = maximumHeight;
    }

    public boolean tryAdd(ServerPlayer player, T entity) {
        if (this.stack.size() >= this.maximumHeight) {
            return false;
        }

        if (this.stack.contains(entity)) {
            return false;
        }

        Entity tail = player;
        if (!this.stack.isEmpty()) {
            tail = this.stack.get(this.stack.size() - 1);
        }

        entity.startRiding(tail, true, true);
        this.stack.add(entity);

        this.onStackChange(player);

        return true;
    }

    public List<T> dropAll(ServerPlayer player) {
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

    private void onStackChange(ServerPlayer player) {
        player.connection.send(new ClientboundSetPassengersPacket(player));

        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(SLOW_MODIFIER_ID);

        if (!this.stack.isEmpty()) {
            double baseValue = attribute.getBaseValue();
            double targetValue = baseValue / (this.stack.size() * 0.3 + 1);

            AttributeModifier modifier = new AttributeModifier(
                    SLOW_MODIFIER_ID,
                    targetValue - baseValue, AttributeModifier.Operation.ADD_VALUE
            );

            attribute.addTransientModifier(modifier);
        }
    }

    public int getHeight() {
        return this.stack.size();
    }

    public boolean isEmpty() {
        return this.stack.isEmpty();
    }
}
