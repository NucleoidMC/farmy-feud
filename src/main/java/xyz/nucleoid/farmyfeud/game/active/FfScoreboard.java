package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.common.widget.SidebarWidget;

public record FfScoreboard(FfActive game,
                           SidebarWidget sidebar) {

    public static FfScoreboard create(FfActive game, GlobalWidgets widgets) {
        Text title = Text.literal("Farmy Feud").formatted(Formatting.GOLD, Formatting.BOLD);
        SidebarWidget sidebar = widgets.addSidebar(title);
        return new FfScoreboard(game, sidebar);
    }

    public void tick() {
        ServerWorld world = this.game.world;
        long time = world.getTime();

        if (time % 20 == 0) {
            this.rerender(time);
        }
    }

    private void rerender(long time) {
        this.sidebar.set(content -> {
            long ticksRemaining = Math.max(this.game.endTime - time, 0);
            long sheepTicksRemaining = Math.max(this.game.nextSpawnTime - time, 0);

            content.add(Text.literal(Formatting.RED.toString() + Formatting.BOLD + "Time left: " + Formatting.RESET + this.renderTime(ticksRemaining)));
            content.add(ScreenTexts.EMPTY);

            content.add(Text.literal(Formatting.BLUE + "Sheep in: " + Formatting.RESET + this.renderTime(sheepTicksRemaining)));
            content.add(ScreenTexts.EMPTY);

            content.add(Text.literal(Formatting.BOLD + "Teams:"));
            this.game.teams().forEach(teamState -> {
                var teamConfig = this.game.teamManager.getTeamConfig(teamState.team);

                String nameFormat = teamConfig.chatFormatting().toString() + Formatting.BOLD;
                String descriptionFormat = Formatting.RESET.toString() + Formatting.GRAY;

                var name = teamConfig.name();
                String description = teamState.getCapturedSheep() + " sheep";

                content.add(Text.literal("  " + nameFormat ).append(name).append(": " + descriptionFormat + description));
            });
        });
    }

    private String renderTime(long ticks) {
        long seconds = (ticks / 20) % 60;
        long minutes = ticks / (20 * 60);

        return String.format("%02d:%02d", minutes, seconds);
    }
}
