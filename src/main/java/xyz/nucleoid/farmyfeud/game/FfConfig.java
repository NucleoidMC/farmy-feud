package xyz.nucleoid.farmyfeud.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.player.GameTeam;

import java.util.List;

public final class FfConfig {
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

    public final Identifier map;
    public final PlayerConfig players;
    public final List<GameTeam> teams;

    public final long gameDuration;
    public final long spawnInterval;

    public final int maxArrows;
    public final long arrowInterval;

    public FfConfig(Identifier map, PlayerConfig players, List<GameTeam> teams, long gameDuration, long spawnInterval, int maxArrows, long arrowInterval) {
        this.map = map;
        this.players = players;
        this.teams = teams;
        this.gameDuration = gameDuration;
        this.spawnInterval = spawnInterval;
        this.maxArrows = maxArrows;
        this.arrowInterval = arrowInterval;
    }
}
