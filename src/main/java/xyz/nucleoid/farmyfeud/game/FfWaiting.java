package xyz.nucleoid.farmyfeud.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.farmyfeud.game.active.FfActive;
import xyz.nucleoid.farmyfeud.game.map.FfMap;
import xyz.nucleoid.farmyfeud.game.map.FfMapBuilder;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.common.team.TeamSelectionLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public final class FfWaiting {
    private final ServerLevel world;
    private final GameSpace gameSpace;
    private final FfMap map;
    private final FfConfig config;

    private final FfSpawnLogic spawnLogic;
    private final TeamSelectionLobby teamSelection;

    private FfWaiting(GameSpace gameSpace, ServerLevel world, FfMap map, FfConfig config, TeamSelectionLobby teamSelection) {
        this.world = world;
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.teamSelection = teamSelection;

        this.spawnLogic = new FfSpawnLogic(this.world, map);
    }

    public static GameOpenProcedure open(GameOpenContext<FfConfig> context) {
        FfConfig config = context.config();

        FfMap map = new FfMapBuilder(config).create(context.server());

        RuntimeLevelConfig worldConfig = new RuntimeLevelConfig()
                .setGenerator(map.createGenerator(context.server()));

        return context.openWithLevel(worldConfig, (game, world) -> {
            var teamSelection = TeamSelectionLobby.addTo(game, config.teams());
            GameWaitingLobby.addTo(game, config.players());
            FfWaiting waiting = new FfWaiting(game.getGameSpace(), world, map, config, teamSelection);

            game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            game.listen(GamePlayerEvents.ACCEPT, player -> player.teleport(world, map.getCenterSpawn() != null ? map.getCenterSpawn().center() : new Vec3(0, 256, 0)));
            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);
        });
    }

    private GameResult requestStart() {
        FfActive.open(this.gameSpace, this.world, this.map, this.config, this.teamSelection);
        return GameResult.ok();
    }

    private void addPlayer(ServerPlayer player) {
        this.spawnPlayer(player);
    }

    private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
        this.spawnPlayer(player);
        return EventResult.DENY;
    }

    private void spawnPlayer(ServerPlayer player) {
        this.spawnLogic.spawnPlayerAtCenter(player);
        this.spawnLogic.resetPlayer(player, GameType.ADVENTURE);
    }
}
