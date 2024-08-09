package to.lodestone.lead.command;

import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.Component;
import to.lodestone.bookshelfapi.api.command.Command;
import to.lodestone.bookshelfapi.api.event.PlayerChatEvent;
import to.lodestone.bookshelfapi.api.util.MiniMessageUtil;
import to.lodestone.lead.LeadPlugin;
import to.lodestone.leadapi.api.ITeam;
import to.lodestone.leadapi.api.ITeamMember;

public class TeamMessageCommand extends Command {

    public TeamMessageCommand(LeadPlugin plugin) {
        super("teammsg");
        aliases("tm", "tc", "tmsg");
        arguments(new GreedyStringArgument("message"));
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
