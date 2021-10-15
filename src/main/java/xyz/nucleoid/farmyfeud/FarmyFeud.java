package xyz.nucleoid.farmyfeud;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.farmyfeud.game.FfConfig;
import xyz.nucleoid.farmyfeud.game.FfWaiting;
import xyz.nucleoid.plasmid.game.GameType;

public final class FarmyFeud implements ModInitializer {
    public static final String ID = "farmy_feud";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    @Override
    public void onInitialize() {
        GameType.register(
                new Identifier(FarmyFeud.ID, "farmy_feud"),
                FfConfig.CODEC,
                FfWaiting::open
        );
    }
}
