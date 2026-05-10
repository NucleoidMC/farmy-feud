package xyz.nucleoid.farmyfeud.game.active;

import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.farmyfeud.FarmyFeud;
import xyz.nucleoid.farmyfeud.entity.FarmSheepEntity;
import xyz.nucleoid.farmyfeud.game.FfConfig;
import xyz.nucleoid.farmyfeud.game.FfSpawnLogic;
import xyz.nucleoid.farmyfeud.game.map.FfMap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.team.*;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.ColoredBlocks;
import xyz.nucleoid.plasmid.api.util.ItemStackBuilder;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.item.ItemUseEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.player.PlayerSwingHandEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class FfActive {
    public static final int RESPAWN_SECONDS = 5;
    public static final int RESPAWN_TICKS = RESPAWN_SECONDS * 20;
    public static final int PICK_UP_INTERVAL_TICKS = 20;
    public static final int LEAP_INTERVAL_TICKS = 5 * 20;

    private static final double LEAP_VELOCITY = 1.2;

    private static final ItemStackBuilder SWORD = ItemStackBuilder.of(Items.STONE_SWORD)
            .setUnbreakable();

    private static final ItemStackBuilder AXE = ItemStackBuilder.of(Items.STONE_AXE)
            .setUnbreakable();

    private static final ItemStackBuilder BOW = ItemStackBuilder.of(Items.BOW)
            .setUnbreakable();

    private static final ItemStackBuilder SADDLE = ItemStackBuilder.of(Items.SADDLE)
            .addLore(Component.literal("Right-click on a sheep to pick it up!"));

    public final ServerLevel world;
    public final GameSpace gameSpace;
    public final FfConfig config;

    public final FfMap map;
    public final FfSpawnLogic spawnLogic;
    public final FfCaptureLogic captureLogic;

    public final FfScoreboard scoreboard;
    private final FfTimerBar timerBar;

    private final Map<GameTeamKey, FfTeamState> teams = new HashMap<>();
    public final TeamManager teamManager;
    private final Map<UUID, FfParticipant> participants = new HashMap<>();

    private long nextArrowTime;
    public long nextSpawnTime;

    public long endTime;

    private long closeTime = -1;

    private FfActive(GameSpace gameSpace, ServerLevel world, FfMap map, FfConfig config, TeamManager manager, GlobalWidgets widgets) {
        this.world = world;
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;

        this.spawnLogic = new FfSpawnLogic(this.world, this.map);
        this.captureLogic = new FfCaptureLogic(this);

        this.teamManager = manager;

        this.scoreboard = FfScoreboard.create(this, widgets);
        this.timerBar = FfTimerBar.create(widgets);

        for (GameTeam team : config.teams()) {
            this.teamManager.addTeam(team);
            this.teams.put(team.key(), new FfTeamState(team.key()));
        }
    }

    public static void open(GameSpace gameSpace, ServerLevel world, FfMap map, FfConfig config, TeamSelectionLobby teamSelection) {
        gameSpace.setActivity(game -> {
            GlobalWidgets widgets = GlobalWidgets.addTo(game);

            var teamManager = TeamManager.addTo(game);

            FfActive active = new FfActive(gameSpace, world, map, config, teamManager, widgets);
            teamSelection.allocate(gameSpace.getPlayers().participants(), (gameTeamKey, player) -> teamManager.addPlayerTo(player, gameTeamKey));

            game.setRule(GameRuleType.CRAFTING, EventResult.DENY);
            game.setRule(GameRuleType.PORTALS, EventResult.DENY);
            game.setRule(GameRuleType.PVP, EventResult.ALLOW);
            game.setRule(GameRuleType.FALL_DAMAGE, EventResult.ALLOW);
            game.setRule(GameRuleType.BLOCK_DROPS, EventResult.DENY);
            game.setRule(GameRuleType.HUNGER, EventResult.DENY);
            game.setRule(GameRuleType.SATURATED_REGENERATION, EventResult.DENY);
            game.setRule(GameRuleType.THROW_ITEMS, EventResult.DENY);

            TeamChat.addTo(game, teamManager);

            game.listen(GameActivityEvents.ENABLE, active::open);
            game.listen(GameActivityEvents.DISABLE, active::close);
            game.listen(GameActivityEvents.STATE_UPDATE, state -> state.canPlay(false));

            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
            game.listen(GamePlayerEvents.ACCEPT, player -> player.teleport(world, map.getCenterSpawn() != null ? map.getCenterSpawn().center() : new Vec3(0, 256, 0)));
            game.listen(GamePlayerEvents.ADD, active::addPlayer);

            game.listen(GameActivityEvents.TICK, active::tick);

            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
            game.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);

            game.listen(ItemUseEvent.EVENT, active::onUseItem);
            game.listen(PlayerSwingHandEvent.EVENT, active::onSwingHand);
        });
    }

    private void open() {
        long time = this.world.getGameTime();

        this.endTime = time + this.config.gameDuration();
        this.initNextSpawnTime(time);
        this.initNextArrowTime(time);

        int sheepCount = (this.config.teams().list().size() * 3) / 2;
        for (int i = 0; i < sheepCount; i++) {
            this.spawnSheep();
        }
    }

    private void close() {
        for (FfParticipant participant : this.participants.values()) {
            ServerPlayer player = participant.player();
            if (player != null) {
                participant.carryStack.dropAll(player);
            }
        }
    }

    private void addPlayer(ServerPlayer player) {
        if (this.gameSpace.getPlayers().participants().contains(player)) {
            FfParticipant participant = this.getOrCreateParticipant(player);
            this.spawnParticipant(player, participant);
        } else {
            this.spawnLogic.spawnPlayerAtCenter(player);
            this.spawnLogic.resetPlayer(player, GameType.SPECTATOR);
        }
    }

    private void tick() {
        long time = this.world.getGameTime();

        if (this.closeTime != -1) {
            if (time >= this.closeTime) {
                this.gameSpace.close(GameCloseReason.FINISHED);
            }
            return;
        }

        this.scoreboard.tick();

        WinResult winResult = this.tickActive(time);
        if (winResult.win()) {
            this.broadcastWinResult(winResult);
            this.closeTime = time + 20 * 5;
        }
    }

    private WinResult tickActive(long time) {
        this.timerBar.update(this.endTime - time, this.config.gameDuration());

        if (time >= this.endTime) {
            return this.computeWinResult();
        }

        this.tickSheepSpawning(time);

        for (FfParticipant participant : this.participants.values()) {
            ServerPlayer player = participant.player();
            if (player == null) {
                continue;
            }

            this.tickParticipant(time, player, participant);
        }

        this.tickArrows(time);

        return WinResult.no();
    }

    private void tickParticipant(long time, ServerPlayer player, FfParticipant participant) {
        if (participant.tryRespawn(time)) {
            this.spawnParticipant(player, participant);
        }

        this.tickSheepStack(player, participant);
    }

    private WinResult computeWinResult() {
        FfTeamState winningTeam = null;
        boolean draw = false;

        for (FfTeamState team : this.teams.values()) {
            if (winningTeam == null) {
                winningTeam = team;
                continue;
            }

            // two teams have the same sheep count!
            if (team.getCapturedSheep() == winningTeam.getCapturedSheep()) {
                draw = true;
            }

            if (team.getCapturedSheep() > winningTeam.getCapturedSheep()) {
                winningTeam = team;
                draw = false;
            }
        }

        if (winningTeam == null || draw) {
            return WinResult.draw();
        }

        return WinResult.win(winningTeam.team);
    }

    private void tickArrows(long time) {
        if (time >= this.nextArrowTime) {
            this.players().forEach(player -> {
                if (player.getInventory().countItem(Items.ARROW) < this.config.maxArrows()) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW));
                }
            });

            this.initNextArrowTime(time);
        }
    }

    private void tickSheepSpawning(long time) {
        if (time >= this.nextSpawnTime) {
            this.spawnSheep();
            this.initNextSpawnTime(time);
        }
    }

    private void initNextSpawnTime(long currentTime) {
        this.nextSpawnTime = currentTime + this.config.spawnInterval();
    }

    private void initNextArrowTime(long currentTime) {
        this.nextArrowTime = currentTime + this.config.arrowInterval();
    }

    private void spawnSheep() {
        BlockBounds centerSpawn = this.map.getCenterSpawn();
        if (centerSpawn == null) {
            return;
        }

        FarmSheepEntity entity = new FarmSheepEntity(this.world, this);

        Vec3 spawnPos = centerSpawn.center();
        entity.snapTo(spawnPos.x, spawnPos.y + 0.5, spawnPos.z, 0.0F, 0.0F);

        entity.setOwnerTeam(null, centerSpawn);
        entity.setInvulnerable(true);

        if (!this.world.addFreshEntity(entity)) {
            FarmyFeud.LOGGER.warn("Chunk not loaded to spawn sheep entity!");
        }
    }

    private void onSwingHand(ServerPlayer player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);

        if (heldStack.getItem() == Items.SADDLE) {
            FfParticipant participant = this.getParticipant(player);
            if (participant != null) {
                List<FarmSheepEntity> entities = participant.carryStack.dropAll(player);
                this.throwEntities(player, entities, 1.0);

                player.playSound(SoundEvents.HORSE_SADDLE.value(), 1.0F, 1.0F);
            }
        }
    }

    private InteractionResult onUseItem(ServerPlayer player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);

        if (heldStack.getItem() == Items.SADDLE) {
            FfParticipant participant = this.getParticipant(player);
            if (participant != null) {
                EntityHitResult traceResult = EntityRayTrace.rayTrace(player, 5.0, 0.5, entity -> {
                    return entity instanceof FarmSheepEntity && !entity.isPassenger() && entity.isPickable();
                });

                if (traceResult != null) {
                    this.tryPickUpSheep(player, participant, (FarmSheepEntity) traceResult.getEntity());
                    return InteractionResult.CONSUME;
                }
            }
        } else if (heldStack.getItem() instanceof AxeItem) {
            ItemCooldowns cooldown = player.getCooldowns();
            if (!cooldown.isOnCooldown(heldStack)) {
                FfParticipant participant = this.getParticipant(player);
                if (participant != null) {
                    Vec3 rotationVec = player.getViewVector(1.0F);
                    player.setDeltaMovement(rotationVec.scale(LEAP_VELOCITY));
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));

                    player.playSound(SoundEvents.HORSE_SADDLE.value(), 1.0F, 1.0F);
                    cooldown.addCooldown(heldStack, LEAP_INTERVAL_TICKS);
                }
            }
        }

        return InteractionResult.PASS;
    }

    public boolean tryPickUpSheep(ServerPlayer player, FfParticipant participant, FarmSheepEntity sheep) {
        GameTeamKey currentTeam = this.captureLogic.getTeamAt(sheep.position());
        if (currentTeam == participant.team) {
            return false;
        }

        if (sheep.tryPickUp(this.world.getGameTime(), participant.team)) {
            return participant.carryStack.tryAdd(player, sheep);
        }

        return false;
    }

    public void tickSheep(FarmSheepEntity sheep) {
        if (this.world.getGameTime() % 10 == 0) {
            GameTeamKey lastPickUpTeam = sheep.getLastPickUpTeam();
            if (lastPickUpTeam != null) {
                GameTeamKey team = this.captureLogic.getTeamAt(sheep.position());
                if (team == lastPickUpTeam) {
                    this.captureLogic.captureSheep(sheep, team);
                }
            }

            if (!sheep.isPassenger() && this.shouldRespawnSheep(sheep)) {
                Vec3 respawnPos = sheep.getLastDropPos();
                if (respawnPos == null) {
                    BlockBounds home = sheep.getHome();
                    if (home == null) {
                        home = this.map.getCenterSpawn();
                    }

                    respawnPos = home.center();
                }

                sheep.unRide();
                sheep.teleportWithPoof(respawnPos.x, respawnPos.y, respawnPos.z);
                sheep.setRemainingFireTicks(0);
            }
        }
    }

    // TODO: if the last pos was inside an illegal region we can't teleport there
    private boolean shouldRespawnSheep(FarmSheepEntity sheep) {
        if (sheep.isInLava()) {
            return true;
        }

        BlockPos sheepPos = sheep.blockPosition();
        for (BlockBounds region : this.map.getIllegalSheepRegions()) {
            if (region.contains(sheepPos)) {
                return true;
            }
        }

        return false;
    }

    private void tickSheepStack(ServerPlayer player, FfParticipant participant) {
        if (participant.carryStack.isEmpty()) {
            return;
        }

        GameTeamKey captureTeam = this.captureLogic.getTeamAt(player.position());
        if (captureTeam == participant.team) {
            List<FarmSheepEntity> entities = participant.carryStack.dropAll(player);
            this.throwEntities(player, entities, 0.5);
        }
    }

    private void throwEntities(ServerPlayer player, Collection<FarmSheepEntity> entities, double strength) {
        Vec3 rotation = player.getViewVector(1.0F);
        for (FarmSheepEntity sheep : entities) {
            sheep.setDeltaMovement(rotation.scale(strength));
            sheep.drop();
        }
    }

    private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
        MutableComponent message;

        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof ServerPlayer) {
            message = player.getDisplayName().copy()
                    .append(Component.literal(" was killed by ").withStyle(ChatFormatting.GRAY))
                    .append(attackerEntity.getDisplayName());
        } else {
            message = player.getDisplayName().copy()
                    .append(Component.literal(" died").withStyle(ChatFormatting.GRAY));
        }

        this.gameSpace.getPlayers().sendMessage(message);

        this.respawnPlayer(player);
        return EventResult.DENY;
    }

    private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
        if (amount < 1.0F) {
            return EventResult.PASS;
        }

        FfParticipant participant = this.getParticipant(player);
        if (participant != null) {
            FfParticipant attackerParticipant = this.getParticipant(source.getEntity());
            if (attackerParticipant != null && attackerParticipant.team == participant.team) {
                return EventResult.DENY;
            }

            participant.carryStack.dropAll(player);
        }

        if (!player.isSpectator() && source.is(DamageTypes.LAVA)) {
            this.respawnPlayer(player);
            return EventResult.DENY;
        }

        return EventResult.PASS;
    }

    private void respawnPlayer(ServerPlayer player) {
        FfParticipant participant = this.getParticipant(player);
        if (participant != null) {
            participant.startRespawn(this.world.getGameTime());
            this.spawnLogic.resetPlayer(player, GameType.SPECTATOR);

            player.sendSystemMessage(Component.literal("You will respawn in " + RESPAWN_SECONDS + " seconds..").withStyle(ChatFormatting.BOLD), false);
        }
    }

    private void spawnParticipant(ServerPlayer player, FfParticipant participant) {
        GameTeamKey team = participant.team;

        var teamConfig = this.teamManager.getTeamConfig(team);

        this.spawnLogic.spawnPlayerAtTeam(player, team);
        this.spawnLogic.resetPlayer(player, GameType.ADVENTURE);

        player.getInventory().add(0, SWORD.build());
        player.getInventory().add(1, AXE.build());
        player.getInventory().add(2, BOW.build());
        player.getInventory().add(4, SADDLE.build());

        player.getInventory().add(8,
                ItemStackBuilder.of(ColoredBlocks.wool(teamConfig.blockDyeColor()))
                        .setName(teamConfig.name().copy().append(" Team")
                                .withStyle(teamConfig.chatFormatting())
                        )
                        .build()
        );

        int color = teamConfig.dyeColor().getValue();
        player.setItemSlot(EquipmentSlot.HEAD, ItemStackBuilder.of(Items.LEATHER_HELMET).setUnbreakable().setDyeColor(color).build());
        player.setItemSlot(EquipmentSlot.CHEST, ItemStackBuilder.of(Items.LEATHER_CHESTPLATE).setUnbreakable().setDyeColor(color).build());
        player.setItemSlot(EquipmentSlot.LEGS, ItemStackBuilder.of(Items.LEATHER_LEGGINGS).setUnbreakable().setDyeColor(color).build());
        player.setItemSlot(EquipmentSlot.FEET, ItemStackBuilder.of(Items.LEATHER_BOOTS).setUnbreakable().setDyeColor(color).build());
    }

    private void broadcastWinResult(WinResult result) {
        GameTeamKey winningTeam = result.winningTeam();

        Component message;
        if (winningTeam != null) {
            var teamConfig = this.teamManager.getTeamConfig(winningTeam);

            message = teamConfig.name().copy().append(" won the game!")
                    .withStyle(ChatFormatting.BOLD, teamConfig.chatFormatting());
        } else {
            message = Component.literal("The game ended in a draw!")
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.GRAY);
        }

        this.gameSpace.getPlayers().sendMessage(message);
    }

    public Stream<FfParticipant> participants() {
        return this.participants.values().stream();
    }

    public Stream<UUID> participantsFor(GameTeamKey team) {
        return this.teams.get(team).participants();
    }

    public Stream<ServerPlayer> players() {
        return this.playersBy(this.participants().map(participant -> participant.playerId));
    }

    public Stream<ServerPlayer> playersFor(GameTeamKey team) {
        return this.playersBy(this.participantsFor(team));
    }

    private Stream<ServerPlayer> playersBy(Stream<UUID> participants) {
        return participants
                .map(id -> (ServerPlayer) this.world.getPlayerByUUID(id))
                .filter(Objects::nonNull);
    }

    public Stream<FfTeamState> teams() {
        return this.teams.values().stream();
    }

    public FfTeamState getTeam(GameTeamKey team) {
        return this.teams.get(team);
    }

    private FfParticipant getOrCreateParticipant(ServerPlayer player) {
        return this.participants.computeIfAbsent(player.getUUID(), uuid -> {
            FfTeamState teamState = this.teams.get(this.teamManager.teamFor(player));

            teamState.addParticipant(uuid);
            this.teamManager.addPlayerTo(player, teamState.team);

            return new FfParticipant(this.gameSpace, player, teamState.team);
        });
    }

    @Nullable
    public FfParticipant getParticipant(Entity entity) {
        if (entity == null) {
            return null;
        }
        return this.participants.get(entity.getUUID());
    }

    private record WinResult(@Nullable GameTeamKey winningTeam, boolean win) {

        public static WinResult win(GameTeamKey winningTeam) {
            return new WinResult(winningTeam, true);
        }

        public static WinResult draw() {
            return new WinResult(null, true);
        }

        public static WinResult no() {
            return new WinResult(null, false);
        }
    }
}
