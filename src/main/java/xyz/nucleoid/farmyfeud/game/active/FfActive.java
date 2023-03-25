package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.farmyfeud.FarmyFeud;
import xyz.nucleoid.farmyfeud.entity.FarmSheepEntity;
import xyz.nucleoid.farmyfeud.game.FfConfig;
import xyz.nucleoid.farmyfeud.game.FfSpawnLogic;
import xyz.nucleoid.farmyfeud.game.map.FfMap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.common.team.*;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.util.ColoredBlocks;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
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
            .addLore(Text.literal("Right-click on a sheep to pick it up!"));

    public final ServerWorld world;
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

    private FfActive(GameSpace gameSpace, ServerWorld world, FfMap map, FfConfig config, TeamManager manager, GlobalWidgets widgets) {
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

    public static void open(GameSpace gameSpace, ServerWorld world, FfMap map, FfConfig config) {
        gameSpace.setActivity(game -> {
            GlobalWidgets widgets = GlobalWidgets.addTo(game);

            var teamManager = TeamManager.addTo(game);

            FfActive active = new FfActive(gameSpace, world, map, config, teamManager, widgets);

            game.setRule(GameRuleType.CRAFTING, ActionResult.FAIL);
            game.setRule(GameRuleType.PORTALS, ActionResult.FAIL);
            game.setRule(GameRuleType.PVP, ActionResult.SUCCESS);
            game.setRule(GameRuleType.FALL_DAMAGE, ActionResult.SUCCESS);
            game.setRule(GameRuleType.BLOCK_DROPS, ActionResult.FAIL);
            game.setRule(GameRuleType.HUNGER, ActionResult.FAIL);
            game.setRule(GameRuleType.THROW_ITEMS, ActionResult.FAIL);

            TeamChat.addTo(game, teamManager);

            game.listen(GameActivityEvents.ENABLE, active::open);
            game.listen(GameActivityEvents.DISABLE, active::close);

            game.listen(GamePlayerEvents.OFFER, player -> player.accept(world, map.getCenterSpawn() != null ? map.getCenterSpawn().center() : new Vec3d(0, 256, 0)));
            game.listen(GamePlayerEvents.ADD, active::addPlayer);

            game.listen(GameActivityEvents.TICK, active::tick);

            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
            game.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);

            game.listen(ItemUseEvent.EVENT, active::onUseItem);
            game.listen(PlayerSwingHandEvent.EVENT, active::onSwingHand);
        });
    }

    private void open() {
        long time = this.world.getTime();

        this.endTime = time + this.config.gameDuration();
        this.initNextSpawnTime(time);
        this.initNextArrowTime(time);

        int sheepCount = (this.config.teams().size() * 3) / 2;
        for (int i = 0; i < sheepCount; i++) {
            this.spawnSheep();
        }
    }

    private void close() {
        for (FfParticipant participant : this.participants.values()) {
            ServerPlayerEntity player = participant.player();
            if (player != null) {
                participant.carryStack.dropAll(player);
            }
        }
    }

    private void addPlayer(ServerPlayerEntity player) {
        FfParticipant participant = this.getOrCreateParticipant(player);
        this.spawnParticipant(player, participant);
    }

    private void tick() {
        long time = this.world.getTime();

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
            ServerPlayerEntity player = participant.player();
            if (player == null) {
                continue;
            }

            this.tickParticipant(time, player, participant);
        }

        this.tickArrows(time);

        return WinResult.no();
    }

    private void tickParticipant(long time, ServerPlayerEntity player, FfParticipant participant) {
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
                if (player.getInventory().count(Items.ARROW) < this.config.maxArrows()) {
                    player.getInventory().offerOrDrop(new ItemStack(Items.ARROW));
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

        Vec3d spawnPos = centerSpawn.center();
        entity.refreshPositionAndAngles(spawnPos.x, spawnPos.y + 0.5, spawnPos.z, 0.0F, 0.0F);

        entity.setOwnerTeam(null, centerSpawn);
        entity.setInvulnerable(true);

        if (!this.world.spawnEntity(entity)) {
            FarmyFeud.LOGGER.warn("Chunk not loaded to spawn sheep entity!");
        }
    }

    private void onSwingHand(ServerPlayerEntity player, Hand hand) {
        ItemStack heldStack = player.getStackInHand(hand);

        if (heldStack.getItem() == Items.SADDLE) {
            FfParticipant participant = this.getParticipant(player);
            if (participant != null) {
                List<FarmSheepEntity> entities = participant.carryStack.dropAll(player);
                this.throwEntities(player, entities, 1.0);

                player.playSound(SoundEvents.ENTITY_HORSE_SADDLE, 1.0F, 1.0F);
            }
        }
    }

    private TypedActionResult<ItemStack> onUseItem(ServerPlayerEntity player, Hand hand) {
        ItemStack heldStack = player.getStackInHand(hand);

        if (heldStack.getItem() == Items.SADDLE) {
            FfParticipant participant = this.getParticipant(player);
            if (participant != null) {
                EntityHitResult traceResult = EntityRayTrace.rayTrace(player, 5.0, 0.5, entity -> {
                    return entity instanceof FarmSheepEntity && !entity.hasVehicle() && entity.canHit();
                });

                if (traceResult != null) {
                    this.tryPickUpSheep(player, participant, (FarmSheepEntity) traceResult.getEntity());
                    return TypedActionResult.consume(heldStack);
                }
            }
        } else if (heldStack.getItem() instanceof AxeItem) {
            ItemCooldownManager cooldown = player.getItemCooldownManager();
            if (!cooldown.isCoolingDown(heldStack.getItem())) {
                FfParticipant participant = this.getParticipant(player);
                if (participant != null) {
                    Vec3d rotationVec = player.getRotationVec(1.0F);
                    player.setVelocity(rotationVec.multiply(LEAP_VELOCITY));
                    player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

                    player.playSound(SoundEvents.ENTITY_HORSE_SADDLE, 1.0F, 1.0F);
                    cooldown.set(heldStack.getItem(), LEAP_INTERVAL_TICKS);
                }
            }
        }

        return TypedActionResult.pass(ItemStack.EMPTY);
    }

    public boolean tryPickUpSheep(ServerPlayerEntity player, FfParticipant participant, FarmSheepEntity sheep) {
        GameTeamKey currentTeam = this.captureLogic.getTeamAt(sheep.getPos());
        if (currentTeam == participant.team) {
            return false;
        }

        if (sheep.tryPickUp(this.world.getTime(), participant.team)) {
            return participant.carryStack.tryAdd(player, sheep);
        }

        return false;
    }

    public void tickSheep(FarmSheepEntity sheep) {
        if (this.world.getTime() % 10 == 0) {
            GameTeamKey lastPickUpTeam = sheep.getLastPickUpTeam();
            if (lastPickUpTeam != null) {
                GameTeamKey team = this.captureLogic.getTeamAt(sheep.getPos());
                if (team == lastPickUpTeam) {
                    this.captureLogic.captureSheep(sheep, team);
                }
            }

            if (!sheep.hasVehicle() && this.shouldRespawnSheep(sheep)) {
                Vec3d respawnPos = sheep.getLastDropPos();
                if (respawnPos == null) {
                    BlockBounds home = sheep.getHome();
                    if (home == null) {
                        home = this.map.getCenterSpawn();
                    }

                    respawnPos = home.center();
                }

                sheep.detach();
                sheep.teleport(respawnPos.x, respawnPos.y, respawnPos.z);
                sheep.setFireTicks(0);
            }
        }
    }

    // TODO: if the last pos was inside an illegal region we can't teleport there
    private boolean shouldRespawnSheep(FarmSheepEntity sheep) {
        if (sheep.isInLava()) {
            return true;
        }

        BlockPos sheepPos = sheep.getBlockPos();
        for (BlockBounds region : this.map.getIllegalSheepRegions()) {
            if (region.contains(sheepPos)) {
                return true;
            }
        }

        return false;
    }

    private void tickSheepStack(ServerPlayerEntity player, FfParticipant participant) {
        if (participant.carryStack.isEmpty()) {
            return;
        }

        GameTeamKey captureTeam = this.captureLogic.getTeamAt(player.getPos());
        if (captureTeam == participant.team) {
            List<FarmSheepEntity> entities = participant.carryStack.dropAll(player);
            this.throwEntities(player, entities, 0.5);
        }
    }

    private void throwEntities(ServerPlayerEntity player, Collection<FarmSheepEntity> entities, double strength) {
        Vec3d rotation = player.getRotationVec(1.0F);
        for (FarmSheepEntity sheep : entities) {
            sheep.setVelocity(rotation.multiply(strength));
            sheep.drop();
        }
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        MutableText message;

        Entity attackerEntity = source.getAttacker();
        if (attackerEntity instanceof ServerPlayerEntity) {
            message = player.getDisplayName().copy()
                    .append(Text.literal(" was killed by ").formatted(Formatting.GRAY))
                    .append(attackerEntity.getDisplayName());
        } else {
            message = player.getDisplayName().copy()
                    .append(Text.literal(" died").formatted(Formatting.GRAY));
        }

        this.gameSpace.getPlayers().sendMessage(message);

        this.respawnPlayer(player);
        return ActionResult.FAIL;
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (amount < 1.0F) {
            return ActionResult.PASS;
        }

        FfParticipant participant = this.getParticipant(player);
        if (participant != null) {
            FfParticipant attackerParticipant = this.getParticipant(source.getAttacker());
            if (attackerParticipant != null && attackerParticipant.team == participant.team) {
                return ActionResult.FAIL;
            }

            participant.carryStack.dropAll(player);
        }

        if (!player.isSpectator() && source.isOf(DamageTypes.LAVA)) {
            this.respawnPlayer(player);
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private void respawnPlayer(ServerPlayerEntity player) {
        FfParticipant participant = this.getParticipant(player);
        if (participant != null) {
            participant.startRespawn(this.world.getTime());
            this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);

            player.sendMessage(Text.literal("You will respawn in " + RESPAWN_SECONDS + " seconds..").formatted(Formatting.BOLD), false);
        }
    }

    private void spawnParticipant(ServerPlayerEntity player, FfParticipant participant) {
        GameTeamKey team = participant.team;

        var teamConfig = this.teamManager.getTeamConfig(team);

        this.spawnLogic.spawnPlayerAtTeam(player, team);
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);

        player.getInventory().insertStack(0, SWORD.build());
        player.getInventory().insertStack(1, AXE.build());
        player.getInventory().insertStack(2, BOW.build());
        player.getInventory().insertStack(4, SADDLE.build());

        player.getInventory().insertStack(8,
                ItemStackBuilder.of(ColoredBlocks.wool(teamConfig.blockDyeColor()))
                        .setName(teamConfig.name().copy().append(" Team")
                                .formatted(teamConfig.chatFormatting())
                        )
                        .build()
        );

        int color = teamConfig.dyeColor().getRgb();
        player.equipStack(EquipmentSlot.HEAD, ItemStackBuilder.of(Items.LEATHER_HELMET).setUnbreakable().setDyeColor(color).build());
        player.equipStack(EquipmentSlot.CHEST, ItemStackBuilder.of(Items.LEATHER_CHESTPLATE).setUnbreakable().setDyeColor(color).build());
        player.equipStack(EquipmentSlot.LEGS, ItemStackBuilder.of(Items.LEATHER_LEGGINGS).setUnbreakable().setDyeColor(color).build());
        player.equipStack(EquipmentSlot.FEET, ItemStackBuilder.of(Items.LEATHER_BOOTS).setUnbreakable().setDyeColor(color).build());
    }

    private void broadcastWinResult(WinResult result) {
        GameTeamKey winningTeam = result.winningTeam();

        Text message;
        if (winningTeam != null) {
            var teamConfig = this.teamManager.getTeamConfig(winningTeam);

            message = teamConfig.name().copy().append(" won the game!")
                    .formatted(Formatting.BOLD, teamConfig.chatFormatting());
        } else {
            message = Text.literal("The game ended in a draw!")
                    .formatted(Formatting.BOLD, Formatting.GRAY);
        }

        this.gameSpace.getPlayers().sendMessage(message);
    }

    public Stream<FfParticipant> participants() {
        return this.participants.values().stream();
    }

    public Stream<UUID> participantsFor(GameTeamKey team) {
        return this.teams.get(team).participants();
    }

    public Stream<ServerPlayerEntity> players() {
        return this.playersBy(this.participants().map(participant -> participant.playerId));
    }

    public Stream<ServerPlayerEntity> playersFor(GameTeamKey team) {
        return this.playersBy(this.participantsFor(team));
    }

    private Stream<ServerPlayerEntity> playersBy(Stream<UUID> participants) {
        return participants
                .map(id -> (ServerPlayerEntity) this.world.getPlayerByUuid(id))
                .filter(Objects::nonNull);
    }

    public Stream<FfTeamState> teams() {
        return this.teams.values().stream();
    }

    public FfTeamState getTeam(GameTeamKey team) {
        return this.teams.get(team);
    }

    private FfParticipant getOrCreateParticipant(ServerPlayerEntity player) {
        return this.participants.computeIfAbsent(player.getUuid(), uuid -> {
            FfTeamState teamState = this.getSmallestTeam();

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
        return this.participants.get(entity.getUuid());
    }

    private FfTeamState getSmallestTeam() {
        List<FfTeamState> teams = new ArrayList<>(this.teams.values());
        Collections.shuffle(teams);

        return teams.stream()
                .min(Comparator.comparingInt(FfTeamState::getParticipantCount))
                .orElseThrow(() -> new IllegalStateException("no teams present!"));
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
