package xyz.nucleoid.farmyfeud.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.game.common.team.GameTeamKey;
import xyz.nucleoid.plasmid.game.world.generator.TemplateChunkGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class FfMap {
    private final MapTemplate template;

    private BlockBounds centerSpawn;
    private final Map<GameTeamKey, TeamRegions> teamRegions = new HashMap<>();

    private final Collection<BlockBounds> illegalSheepRegions = new ArrayList<>();

    public FfMap(MapTemplate template) {
        this.template = template;
    }

    public void setCenterSpawn(BlockBounds centerSpawn) {
        this.centerSpawn = centerSpawn;
    }

    public void addTeamRegions(GameTeamKey team, TeamRegions regions) {
        this.teamRegions.put(team, regions);
        this.addIllegalSheepRegion(regions.spawn);
    }

    public void addIllegalSheepRegion(BlockBounds bounds) {
        this.illegalSheepRegions.add(bounds);
    }

    @Nullable
    public BlockBounds getCenterSpawn() {
        return this.centerSpawn;
    }

    @Nullable
    public TeamRegions getTeamRegions(GameTeamKey team) {
        return this.teamRegions.get(team);
    }

    public Collection<BlockBounds> getIllegalSheepRegions() {
        return this.illegalSheepRegions;
    }

    public ChunkGenerator createGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }

    public record TeamRegions(BlockBounds spawn, BlockBounds pen) {
    }
}
