package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.common.widget.BossBarWidget;

public record FfTimerBar(BossBarWidget widget) {

    static FfTimerBar create(GlobalWidgets widgets) {
        LiteralText title = new LiteralText("Game ends in...");
        return new FfTimerBar(widgets.addBossBar(title, BossBar.Color.RED, BossBar.Style.NOTCHED_10));
    }

    public void update(long timeLeft, long totalTime) {
        if (timeLeft % 20 == 0) {
            this.widget.setTitle(this.getText(timeLeft));
            this.widget.setProgress((float) timeLeft / totalTime);
        }
    }

    private Text getText(long tickUntilEnd) {
        long secondsUntilEnd = tickUntilEnd / 20;

        long minutes = secondsUntilEnd / 60;
        long seconds = secondsUntilEnd % 60;
        String time = String.format("%02d:%02d", minutes, seconds);

        return new LiteralText("Game ends in: " + time + "...");
    }
}
