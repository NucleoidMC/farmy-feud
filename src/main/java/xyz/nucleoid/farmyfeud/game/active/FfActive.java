package xyz.nucleoid.farmyfeud.game.active;

import net.fabricmc.fabric.api.tool.attribute.v1.FabricToolTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
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
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameCloseListener;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.HandSwingListener;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.UseItemListener;
import xyz.nucleoid.plasmid.game.player.GameTeam;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.BlockBounds;
import xyz.nucleoid.plasmid.util.ColoredBlocks;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;

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
            .addLore(new LiteralText("Right-click on a sheep to pick it up!"));

    public final ServerWorld world;
    public final GameSpace gameSpace;
    public final FfConfig config;

    public final FfMap map;
    public final FfSpawnLogic spawnLogic;
    public final FfCaptureLogic captureLogic;

    public final FfScoreboard scoreboard;
    private final FfTimerBar timerBar;

    private final Map<GameTeam, FfTeamState> teams = new HashMap<>();
    private final Map<UUID, FfParticipant> participants = new HashMap<>();

    private long nextArrowTime;
    public long nextSpawnTime;

    public long endTime;

    private long closeTime = -1;

    private FfActive(GameSpace gameSpace, FfMap map, FfConfig config, GlobalWidgets widgets) {
        this.world = gameSpace.getWorld();
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;

        this.spawnLogic = new FfSpawnLogic(this.world, this.map);
        this.captureLogic = new FfCaptureLogic(this);

        this.scoreboard = gameSpace.addResource(FfScoreboard.create(this, widgets));
        this.timerBar = FfTimerBar.create(widgets);

        for (GameTeam team : config.teams) {
            this.teams.put(team, new FfTeamState(team));
        }
    }

    public static void open(GameSpace gameSpace, FfMap map, FfConfig config) {
        gameSpace.openGame(game -> {
            GlobalWidgets widgets = new GlobalWidgets(game);

            FfActive active = new FfActive(gameSpace, map, config, widgets);

            game.setRule(GameRule.CRAFTING, RuleResult.DENY);
            game.setRule(GameRule.PORTALS, RuleResult.DENY);
            game.setRule(GameRule.PVP, RuleResult.ALLOW);
            game.setRule(GameRule.FALL_DAMAGE, RuleResult.ALLOW);
            game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            game.setRule(GameRule.HUNGER, RuleResult.DENY);
            game.setRule(GameRule.THROW_ITEMS, RuleResult.DENY);
            game.setRule(GameRule.TEAM_CHAT, RuleResult.ALLOW);

            game.on(GameOpenListener.EVENT, active::open);
            game.on(GameCloseListener.EVENT, active::close);

            game.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            game.on(PlayerAddListener.EVENT, active::addPlayer);

            game.on(GameTickListener.EVENT, active::tick);

            game.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
            game.on(PlayerDamageListener.EVENT, active::onPlayerDamage);

            game.on(UseItemListener.EVENT, active::onUseItem);
            game.on(HandSwingListener.EVENT, active::onSwingHand);
        });
    }

    private void open() {
        long time = this.world.getTime();

        this.endTime = time + this.config.gameDuration;
        this.initNextSpawnTime(time);
        this.initNextArrowTime(time);

        int sheepCount = (this.config.teams.size() * 3) / 2;
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
                this.gameSpace.close();
            }
            return;
        }

        this.scoreboard.tick();

        WinResult winResult = this.tickActive(time);
        if (winResult.isWin()) {
            this.broadcastWinResult(winResult);
            this.closeTime = time + 20 * 5;
        }
    }

    private WinResult tickActive(long time) {
        this.timerBar.update(this.endTime - time, this.config.gameDuration);

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

        if (winningTeam == null || draw == true) {
            return WinResult.draw();
        }

        return WinResult.win(winningTeam.team);
    }

    private void tickArrows(long time) {
        if (time >= this.nextArrowTime) {
            this.players().forEach(player -> {
                if (player.inventory.count(Items.ARROW) < this.config.maxArrows) {
                    player.inventory.offerOrDrop(this.world, new ItemStack(Items.ARROW));
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
        this.nextSpawnTime = currentTime + this.config.spawnInterval;
    }

    private void initNextArrowTime(long currentTime) {
        this.nextArrowTime = currentTime + this.config.arrowInterval;
    }

    private void spawnSheep() {
        BlockBounds centerSpawn = this.map.getCenterSpawn();
        if (centerSpawn == null) {
            return;
        }

        FarmSheepEntity entity = new FarmSheepEntity(this.world, this);

        Vec3d spawnPos = centerSpawn.getCenter();
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
                    return entity instanceof FarmSheepEntity && !entity.hasVehicle() && entity.collides();
                });

                if (traceResult != null) {
                    this.tryPickUpSheep(player, participant, (FarmSheepEntity) traceResult.getEntity());
                    return TypedActionResult.consume(heldStack);
                }
            }
        } else if (heldStack.getItem().isIn(FabricToolTags.AXES)) {
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
        GameTeam currentTeam = this.captureLogic.getTeamAt(sheep.getPos());
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
            GameTeam lastPickUpTeam = sheep.getLastPickUpTeam();
            if (lastPickUpTeam != null) {
                GameTeam team = this.captureLogic.getTeamAt(sheep.getPos());
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

                    respawnPos = home.getCenter();
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

        GameTeam captureTeam = this.captureLogic.getTeamAt(player.getPos());
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
            message = player.getDisplayName().shallowCopy()
                    .append(new LiteralText(" was killed by ").formatted(Formatting.GRAY))
                    .append(attackerEntity.getDisplayName());
        } else {
            message = player.getDisplayName().shallowCopy()
                    .append(new LiteralText(" died").formatted(Formatting.GRAY));
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

        if (!player.isSpectator() && source == DamageSource.LAVA) {
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

            player.sendMessage(new LiteralText("You will respawn in " + RESPAWN_SECONDS + " seconds..").formatted(Formatting.BOLD), false);
        }
    }

    private void spawnParticipant(ServerPlayerEntity player, FfParticipant participant) {
        GameTeam team = participant.team;

        this.spawnLogic.spawnPlayerAtTeam(player, team);
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);

        player.inventory.insertStack(0, SWORD.build());
        player.inventory.insertStack(1, AXE.build());
        player.inventory.insertStack(2, BOW.build());
        player.inventory.insertStack(4, SADDLE.build());

        player.inventory.insertStack(8,
                ItemStackBuilder.of(ColoredBlocks.wool(team.getDye()))
                        .setName(new LiteralText(team.getDisplay() + " Team")
                                .formatted(team.getFormatting())
                        )
                        .build()
        );

        int color = team.getColor();
        player.equipStack(EquipmentSlot.HEAD, ItemStackBuilder.of(Items.LEATHER_HELMET).setUnbreakable().setColor(color).build());
        player.equipStack(EquipmentSlot.CHEST, ItemStackBuilder.of(Items.LEATHER_CHESTPLATE).setUnbreakable().setColor(color).build());
        player.equipStack(EquipmentSlot.LEGS, ItemStackBuilder.of(Items.LEATHER_LEGGINGS).setUnbreakable().setColor(color).build());
        player.equipStack(EquipmentSlot.FEET, ItemStackBuilder.of(Items.LEATHER_BOOTS).setUnbreakable().setColor(color).build());
    }

    private void broadcastWinResult(WinResult result) {
        GameTeam winningTeam = result.getWinningTeam();

        Text message;
        if (winningTeam != null) {
            message = new LiteralText(winningTeam.getDisplay() + " won the game!")
                    .formatted(Formatting.BOLD, winningTeam.getFormatting());
        } else {
            message = new LiteralText("The game ended in a draw!")
                    .formatted(Formatting.BOLD, Formatting.GRAY);
        }

        this.gameSpace.getPlayers().sendMessage(message);
    }

    public Stream<FfParticipant> participants() {
        return this.participants.values().stream();
    }

    public Stream<UUID> participantsFor(GameTeam team) {
        return this.teams.get(team).participants();
    }

    public Stream<ServerPlayerEntity> players() {
        return this.playersBy(this.participants().map(participant -> participant.playerId));
    }

    public Stream<ServerPlayerEntity> playersFor(GameTeam team) {
        return this.playersBy(this.participantsFor(team));
    }

    private Stream<ServerPlayerEntity> playersBy(Stream<UUID> participants) {
        ServerWorld world = this.gameSpace.getWorld();
        return participants
                .map(id -> (ServerPlayerEntity) world.getPlayerByUuid(id))
                .filter(Objects::nonNull);
    }

    public Stream<FfTeamState> teams() {
        return this.teams.values().stream();
    }

    public FfTeamState getTeam(GameTeam team) {
        return this.teams.get(team);
    }

    private FfParticipant getOrCreateParticipant(ServerPlayerEntity player) {
        return this.participants.computeIfAbsent(player.getUuid(), uuid -> {
            FfTeamState teamState = this.getSmallestTeam();

            teamState.addParticipant(uuid);
            this.scoreboard.addPlayer(player, teamState.team);

            return new FfParticipant(player, teamState.team);
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

    private static class WinResult {
        private final GameTeam winningTeam;
        private final boolean win;

        private WinResult(GameTeam winningTeam, boolean win) {
            this.winningTeam = winningTeam;
            this.win = win;
        }

        public static WinResult win(GameTeam winningTeam) {
            return new WinResult(winningTeam, true);
        }

        public static WinResult draw() {
            return new WinResult(null, true);
        }

        public static WinResult no() {
            return new WinResult(null, false);
        }

        @Nullable
        public GameTeam getWinningTeam() {
            return this.winningTeam;
        }

        public boolean isWin() {
            return this.win;
        }
    }
}
