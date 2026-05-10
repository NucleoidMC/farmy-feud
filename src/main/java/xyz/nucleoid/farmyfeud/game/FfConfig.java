package xyz.nucleoid.farmyfeud.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamList;

import java.util.List;
import net.minecraft.resources.Identifier;

public record FfConfig(Identifier map, WaitingLobbyConfig players,
                       GameTeamList teams, long gameDuration,
                       long spawnInterval, int maxArrows, long arrowInterval) {
    public static final MapCodec<FfConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        return instance.group(
                Identifier.CODEC.fieldOf("map").forGetter(config -> config.map),
                WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(config -> config.players),
                GameTeamList.CODEC.fieldOf("teams").forGetter(config -> config.teams),
                Codec.LONG.optionalFieldOf("game_duration", 60L * 8 * 20).forGetter(config -> config.gameDuration),
                Codec.LONG.optionalFieldOf("spawn_interval", 30L * 30).forGetter(config -> config.spawnInterval),
                Codec.INT.optionalFieldOf("max_arrows", 3).forGetter(config -> config.maxArrows),
                Codec.LONG.optionalFieldOf("arrow_interval", 20L * 10).forGetter(config -> config.arrowInterval)
        ).apply(instance, FfConfig::new);
    });
}
