package xyz.nucleoid.farmyfeud.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.common.team.GameTeam;

import java.util.List;

public record FfConfig(Identifier map, PlayerConfig players,
                       List<GameTeam> teams, long gameDuration,
                       long spawnInterval, int maxArrows, long arrowInterval) {
    public static final Codec<FfConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Identifier.CODEC.fieldOf("map").forGetter(config -> config.map),
                PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.players),
                GameTeam.CODEC.listOf().fieldOf("teams").forGetter(config -> config.teams),
                Codec.LONG.optionalFieldOf("game_duration", 60L * 8 * 20).forGetter(config -> config.gameDuration),
                Codec.LONG.optionalFieldOf("spawn_interval", 30L * 30).forGetter(config -> config.spawnInterval),
                Codec.INT.optionalFieldOf("max_arrows", 3).forGetter(config -> config.maxArrows),
                Codec.LONG.optionalFieldOf("arrow_interval", 20L * 10).forGetter(config -> config.arrowInterval)
        ).apply(instance, FfConfig::new);
    });
}
