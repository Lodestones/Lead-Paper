package to.lodestone.lead.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.arguments.StringArgument;
import to.lodestone.bookshelfapi.api.command.Command;
import to.lodestone.bookshelfapi.api.util.MiniMessageUtil;
import to.lodestone.lead.LeadPlugin;

public class LeadCommand extends Command {
    public LeadCommand(LeadPlugin plugin) {
        super("lead");
        permission("lodestone.lead.commands.lead");
        subCommand(new Command("reload")
                .permission("lodestone.lead.commands.reload")
                .optionalArguments(new StringArgument("-t"))
                .executesPlayer((player, args) -> {
                    boolean reloadTeams = args.get(0) instanceof String s && s.equalsIgnoreCase("-t");
                    long timeAt = System.currentTimeMillis();
                    player.sendMessage(MiniMessageUtil.deserialize("<italic><gray>[Lead: Reloading...]"));
                    plugin.reload(reloadTeams);
                    plugin.getServer().getOnlinePlayers().forEach(CommandAPI::updateRequirements);
                    player.sendMessage(MiniMessageUtil.deserialize("<italic><gray>[Lead: Reloaded in %s ms!]", System.currentTimeMillis() - timeAt));
                })
        );
    }
}
