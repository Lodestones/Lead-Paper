package gg.lode.lead.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
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
        withSubcommand(new CommandAPICommand("update")
                .withPermission("lodestone.lead.commands.update")
                .executesPlayer((player, args) -> {
                    plugin.update();
                    player.sendMessage(MiniMessageUtil.deserialize("<green>Successfully updated all teams!"));
                })
        );
        withSubcommand(new CommandAPICommand("auto_assign")
                .withPermission("lodestone.lead.commands.auto_assign")
                .withOptionalArguments(new StringArgument("value").replaceSuggestions(ArgumentSuggestions.strings(s -> new String[]{"on", "off"})))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof String value) {
                        switch (value.toLowerCase()) {
                            case "on", "true", "1" -> {
                                if (plugin.config().getBoolean("auto_assign")) {
                                    sender.sendMessage(MiniMessageUtil.deserialize("<red>Auto assign is already enabled"));
                                    return;
                                }

                                plugin.config().set("auto_assign", true);
                                plugin.config().save();
                                sender.sendMessage(MiniMessageUtil.deserialize("<green>Auto assign is now enabled"));
                            }
                            case "off", "false", "0" -> {
                                if (!plugin.config().getBoolean("auto_assign")) {
                                    sender.sendMessage(MiniMessageUtil.deserialize("<red>Auto assign is already disabled"));
                                    return;
                                }

                                plugin.config().set("auto_assign", false);
                                plugin.config().save();
                                sender.sendMessage(MiniMessageUtil.deserialize("<green>Auto assign is now disabled"));
                            }
                        }
                    } else {
                        plugin.config().set("auto_assign", !plugin.config().getBoolean("auto_assign"));
                        plugin.config().save();

                        sender.sendMessage(MiniMessageUtil.deserialize("<green>Auto assign is now %s", plugin.config().getBoolean("auto_assign") ? "enabled" : "disabled"));
                    }
                })
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
