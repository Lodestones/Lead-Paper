package gg.lode.lead.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.bookshelfapi.api.util.VariableContext;
import gg.lode.lead.LeadPlugin;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import gg.lode.leadapi.api.event.TeamMessageEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class TeamMessageCommand extends CommandAPICommand {
    public TeamMessageCommand(LeadPlugin plugin) {
        super("teammsg");
        withAliases("tm", "tc", "tmsg");
        @Nullable String commandPermission = plugin.config().getString("permissions.teammsg");
        if (commandPermission != null) withPermission(commandPermission);
        withArguments(new GreedyStringArgument("message"));
        executesPlayer((player, args) -> {
            ITeam team = plugin.getTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(MiniMessageHelper.deserialize("<red>You are not in a team!"));
                return;
            }

            if (args.get("message") instanceof String message) {
                VariableContext ctx = new VariableContext();
                String font = plugin.config().getString("font", "default");
                boolean colorNames = plugin.config().getBoolean("color_names");

                TeamMessageEvent teamMessageEvent = new TeamMessageEvent(player, message);
                if (teamMessageEvent.callEvent()) {
                    ctx.set("font", font);
                    ctx.set("teamName", team.getName());
                    ctx.set("teamColor", team.getColor());
                    ctx.set("playerName", player.getName());
                    ctx.set("message", message);
                    ctx.set("messageColor", "#FFFFFF");
                    ctx.set("colorNames", colorNames ? String.format("<%s>", team.getColor()) : "<reset>");

                    Component prefix = MiniMessageHelper.deserialize("<green><bold>TEAM » ");
                    Component teamLabel = ctx.replaceAsComponent("<reset><font:<font>><<teamColor>><teamName></font><colorNames> ");
                    Component messageColor = ctx.replaceAsComponent("<playerName>: <message>");

                    for (ITeamMember member : team.getMembers()) {
                        Player memberPlayer = plugin.getServer().getPlayer(member.getUniqueId());
                        if (memberPlayer != null) {
                            Component messageToSend = MiniMessageHelper.persistStyle(prefix, teamLabel).append(messageColor);
                            memberPlayer.sendMessage(messageToSend);
                        }
                    }
                }
            }
        });
    }
}
