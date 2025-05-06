package gg.lode.lead.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import gg.lode.bookshelfapi.api.event.PlayerChatEvent;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.lead.LeadPlugin;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import net.kyori.adventure.text.Component;

public class TeamMessageCommand extends CommandAPICommand {
    public TeamMessageCommand(LeadPlugin plugin) {
        super("teammsg");
        withAliases("tm", "tc", "tmsg");
        withArguments(new GreedyStringArgument("message"));
        executesPlayer((player, args) -> {
            ITeam team = plugin.getTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(MiniMessageUtil.deserialize("<red>You are not in a team!"));
                return;
            }

            if (args.get("message") instanceof String message) {
                PlayerChatEvent playerChatEvent = new PlayerChatEvent(player, MiniMessageUtil.deserialize("<green><bold>TEAM »<reset>"), Component.text(message));
                playerChatEvent.playerColor(team.getColor());
                playerChatEvent.messageColor("#FFFFFF");
                playerChatEvent.setViewers(team.getMembers().stream().map(ITeamMember::getUniqueId).toList());
                playerChatEvent.callEvent();
            }
        });
    }
}
