package xyz.nucleoid.farmyfeud.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.biome.BiomeKeys;
import xyz.nucleoid.farmyfeud.FarmyFeud;
import xyz.nucleoid.farmyfeud.game.FfConfig;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateMetadata;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.game.common.team.GameTeam;

import java.io.IOException;

public record FfMapBuilder(FfConfig config) {
    public FfMap create(MinecraftServer server) {
        MapTemplate template = MapTemplate.createEmpty();
        try {
            template = MapTemplateSerializer.loadFromResource(server, this.config.map());
        } catch (IOException e) {
            FarmyFeud.LOGGER.error("Failed to load map template", e);
        }

        FfMap map = new FfMap(template);

        MapTemplateMetadata metadata = template.getMetadata();
        this.addGlobalRegions(map, metadata);
        this.addTeamRegions(map, metadata);

        template.setBiome(BiomeKeys.PLAINS);

        return map;
    }

    private void addGlobalRegions(FfMap map, MapTemplateMetadata metadata) {
        BlockBounds sheepSpawn = metadata.getFirstRegionBounds("sheep_spawn");
        map.setCenterSpawn(sheepSpawn);

        metadata.getRegionBounds("sheep_die").forEach(map::addIllegalSheepRegion);
    }

    private void addTeamRegions(FfMap map, MapTemplateMetadata metadata) {
        for (GameTeam team : this.config.teams()) {
            String key = team.key().id();

            BlockBounds spawn = metadata.getFirstRegionBounds(key + "_spawn");
            if (spawn == null) {
                FarmyFeud.LOGGER.warn("Missing '{}_spawn'", key);
            }

            BlockBounds pen = metadata.getFirstRegionBounds(key + "_pen");
            if (pen == null) {
                FarmyFeud.LOGGER.warn("Missing '{}_pen'", key);
            }

            map.addTeamRegions(team.key(), new FfMap.TeamRegions(spawn, pen));
        }
    }
}
