package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.BossBarWidget;

public record FfTimerBar(BossBarWidget widget) {

    static FfTimerBar create(GlobalWidgets widgets) {
        Component title = Component.literal("Game ends in...");
        return new FfTimerBar(widgets.addBossBar(title, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
    }

    public void update(long timeLeft, long totalTime) {
        if (timeLeft % 20 == 0) {
            this.widget.setTitle(this.getText(timeLeft));
            this.widget.setProgress((float) timeLeft / totalTime);
        }
    }

    private Component getText(long tickUntilEnd) {
        long secondsUntilEnd = tickUntilEnd / 20;

        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%02d:%02d", minutes, seconds);

        return Component.literal("Game ends in: " + time + "...");
    }
}
