package xyz.nucleoid.farmyfeud.game.active;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.common.widget.SidebarWidget;

public record FfScoreboard(FfActive game,
                           SidebarWidget sidebar) {

    public static FfScoreboard create(FfActive game, GlobalWidgets widgets) {
        Component title = Component.literal("Farmy Feud").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        SidebarWidget sidebar = widgets.addSidebar(title);
        return new FfScoreboard(game, sidebar);
    }

    public void tick() {
        ServerLevel world = this.game.world;
        long time = world.getGameTime();

        if (time % 20 == 0) {
            this.rerender(time);
        }
    }

    private void rerender(long time) {
        this.sidebar.set(content -> {
            long ticksRemaining = Math.max(this.game.endTime - time, 0);
            long sheepTicksRemaining = Math.max(this.game.nextSpawnTime - time, 0);

            content.add(Component.literal(ChatFormatting.RED.toString() + ChatFormatting.BOLD + "Time left: " + ChatFormatting.RESET + this.renderTime(ticksRemaining)));
            content.add(CommonComponents.EMPTY);

            content.add(Component.literal(ChatFormatting.BLUE + "Sheep in: " + ChatFormatting.RESET + this.renderTime(sheepTicksRemaining)));
            content.add(CommonComponents.EMPTY);

            content.add(Component.literal(ChatFormatting.BOLD + "Teams:"));
            this.game.teams().forEach(teamState -> {
                var teamConfig = this.game.teamManager.getTeamConfig(teamState.team);

                String nameFormat = teamConfig.chatFormatting().toString() + ChatFormatting.BOLD;
                String descriptionFormat = ChatFormatting.RESET.toString() + ChatFormatting.GRAY;

                var name = teamConfig.name();
                String description = teamState.getCapturedSheep() + " sheep";

                content.add(Component.literal("  " + nameFormat ).append(name).append(": " + descriptionFormat + description));
            });
        });
    }

    private String renderTime(long ticks) {
        long seconds = (ticks / 20) % 60;
        long minutes = ticks / (20 * 60);

        return String.format("%02d:%02d", minutes, seconds);
    }
}
