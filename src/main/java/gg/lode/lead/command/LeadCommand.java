package gg.lode.lead.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandExecutor;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.lead.LeadPlugin;

public class LeadCommand extends CommandAPICommand {
    public LeadCommand(LeadPlugin plugin) {
        super("lead");
        withPermission("lodestone.lead.commands.lead");
        withSubcommand(new CommandAPICommand("version")
                .executes((CommandExecutor) (sender, args) -> sender.sendMessage(MiniMessageUtil.deserialize("Running Lead %s", LeadPlugin.VERSION)))
        );
        withSubcommand(new CommandAPICommand("reload")
                .withPermission("lodestone.lead.commands.reload")
                .withOptionalArguments(new StringArgument("-t"))
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
