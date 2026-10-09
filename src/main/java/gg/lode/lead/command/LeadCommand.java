package gg.lode.lead.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandExecutor;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.bookshelfapi.api.util.VariableContext;
import gg.lode.lead.LeadPlugin;

public class LeadCommand extends CommandAPICommand {
    public LeadCommand(LeadPlugin plugin) {
        super("lead");
        withPermission("lodestone.lead.commands.lead");
        withSubcommand(new CommandAPICommand("version")
                .executes((CommandExecutor) (sender, args) -> sender.sendMessage(plugin.message(sender, "lead.command.version", VariableContext.of("version", plugin.getVersion()))))
        );
        withSubcommand(new CommandAPICommand("update")
                .withPermission("lodestone.lead.commands.update")
                .executesPlayer((player, args) -> {
                    plugin.update();
                    player.sendMessage(plugin.message(player, "lead.command.update.success"));
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
                                    sender.sendMessage(plugin.message(sender, "lead.command.auto_assign.already_enabled"));
                                    return;
                                }

                                plugin.config().set("auto_assign", true);
                                plugin.config().save();
                                sender.sendMessage(plugin.message(sender, "lead.command.auto_assign.enabled"));
                            }
                            case "off", "false", "0" -> {
                                if (!plugin.config().getBoolean("auto_assign")) {
                                    sender.sendMessage(plugin.message(sender, "lead.command.auto_assign.already_disabled"));
                                    return;
                                }

                                plugin.config().set("auto_assign", false);
                                plugin.config().save();
                                sender.sendMessage(plugin.message(sender, "lead.command.auto_assign.disabled"));
                            }
                        }
                    } else {
                        plugin.config().set("auto_assign", !plugin.config().getBoolean("auto_assign"));
                        plugin.config().save();

                        sender.sendMessage(plugin.message(sender, "lead.command.auto_assign.state", VariableContext.of("state", plugin.config().getBoolean("auto_assign") ? "enabled" : "disabled")));
                    }
                })
        );
        withSubcommand(new CommandAPICommand("reload")
                .withPermission("lodestone.lead.commands.reload")
                .withOptionalArguments(new StringArgument("-t"))
                .executesPlayer((player, args) -> {
                    boolean reloadTeams = args.get(0) instanceof String s && s.equalsIgnoreCase("-t");
                    long timeAt = System.currentTimeMillis();
                    player.sendMessage(plugin.message(player, "lead.command.reload.reloading"));
                    plugin.reload(reloadTeams);
                    plugin.getServer().getOnlinePlayers().forEach(CommandAPI::updateRequirements);
                    player.sendMessage(plugin.message(player, "lead.command.reload.done", VariableContext.of("ms", String.valueOf(System.currentTimeMillis() - timeAt))));
                })
        );
    }
}
