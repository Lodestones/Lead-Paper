package gg.lode.lead.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandArguments;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.util.EnumUtil;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.bookshelfapi.api.util.StringUtil;
import gg.lode.lead.LeadPlugin;
import gg.lode.lead.menu.TeamEditorMenu;
import gg.lode.lead.menu.TeamListMenu;
import gg.lode.lead.team.TeamMember;
import gg.lode.leadapi.api.GeneratorType;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import gg.lode.leadapi.api.event.*;
import gg.lode.leadapi.api.exception.TeamAlreadyExistsException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TeamCommand extends CommandAPICommand {
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final List<UUID> teamReset = new ArrayList<>();

    public TeamCommand(LeadPlugin plugin) {
        super("team");
        @Nullable String commandPermission = plugin.config().getString("permissions.team");
        if (commandPermission != null) withPermission(commandPermission);
        withAliases("t");
        withSubcommand(new CommandAPICommand("kick")
                .withPermission(plugin.config().getString("commands.kick", null))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team != null && team.getLeaderUniqueId() != null && team.containsMember(team.getLeaderUniqueId()) && team.getLeaderUniqueId().toString().equalsIgnoreCase(player.getUniqueId().toString());
                    }

                    return false;
                })
                .withArguments(new OfflinePlayerArgument("target"))
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team == null) {
                        player.sendMessage(Component.text("You are not in a team!").color(NamedTextColor.RED));
                        return;
                    }

                    if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(Component.text("You are not the team leader!").color(NamedTextColor.RED));
                        return;
                    }

                    if (args.get(0) instanceof OfflinePlayer target) {
                        if (target.getName() == null) {
                            player.sendMessage(MiniMessageUtil.deserialize("<red>That player is not online"));
                            return;
                        }

                        if (target.getName().equalsIgnoreCase(player.getName())) {
                            player.sendMessage(Component.text("You cannot kick yourself.").color(NamedTextColor.RED));
                            return;
                        }

                        TeamKickEvent kickEvent = new TeamKickEvent(team, target);
                        if (kickEvent.callEvent()) {
                            for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                Player p = plugin.getServer().getPlayer(playerUniqueId);
                                if (p != null)
                                    p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>PLAYER KICKED\n  <reset><yellow>%s</yellow> <gray>has been kicked from the team!\n ", target.getName())));
                            }

                            team.removeMember(target.getUniqueId());
                            if (plugin.config().getBoolean("automatic_updates", true))
                                plugin.update();

                            CommandAPI.updateRequirements(player);
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("leave")
                .withPermission(plugin.config().getString("commands.leave", null))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team != null && team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().toString().equalsIgnoreCase(player.getUniqueId().toString());
                    }

                    return false;
                })
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team == null) {
                        player.sendMessage(Component.text("You are not in a team!").color(NamedTextColor.RED));
                        return;
                    }

                    if (team.getLeaderUniqueId() != null && team.getLeaderUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(Component.text("You are the team leader! Consider running \"/team disband\".").color(NamedTextColor.RED));
                        return;
                    }

                    TeamLeaveEvent teamLeaveEvent = new TeamLeaveEvent(player, team);
                    if (teamLeaveEvent.callEvent()) {
                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                            if (p != null)
                                p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>PLAYER LEFT\n  <reset><yellow>%s</yellow> <gray>has left the team!\n ", player.getName())));
                        }

                        team.removeMember(player.getUniqueId());
                        if (plugin.config().getBoolean("automatic_updates", true))
                            plugin.update();

                        CommandAPI.updateRequirements(player);
                    }
                })
        );
        withSubcommand(new CommandAPICommand("disband")
                .withPermission(plugin.config().getString("commands.disband", null))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team != null && team.getLeaderUniqueId() != null && team.containsMember(team.getLeaderUniqueId()) && team.getLeaderUniqueId().toString().equalsIgnoreCase(player.getUniqueId().toString());
                    }

                    return false;
                })
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team == null) {
                        player.sendMessage(Component.text("You are not in a team!").color(NamedTextColor.RED));
                        return;
                    }

                    if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(Component.text("You are not the team leader!").color(NamedTextColor.RED));
                        return;
                    }

                    TeamDisbandEvent disbandEvent = new TeamDisbandEvent(team);
                    if (disbandEvent.callEvent()) {
                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                            if (p != null)
                                p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>TEAM DISBANDED \n  <reset><yellow>%s</yellow> <gray>has disbanded the team!\n ", player.getName())));
                        }

                        this.cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (player.isOp() ? 3000 : 10000));

                        plugin.deleteTeam(team);
                        if (plugin.config().getBoolean("automatic_updates", true))
                            plugin.update();

                        CommandAPI.updateRequirements(player);
                    }
                })
        );
        withSubcommand(new CommandAPICommand("edit")
                .withPermission(plugin.config().getString("commands.edit", "lodestone.lead.manage"))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executesPlayer((player, args) -> {
                    if (args.get(0) instanceof String id) {
                        ITeam team = plugin.getTeam(id);
                        if (team == null) {
                            player.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                            return;
                        }

                        new TeamEditorMenu(plugin, player, team).open();
                    }
                })
        );
        withSubcommand(new CommandAPICommand("invite")
                .withPermission(plugin.config().getString("commands.invite", null))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team != null;
                    }

                    return false;
                })
                .withArguments(new EntitySelectorArgument.OnePlayer("target"))
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team == null) {
                        player.sendMessage(Component.text("You are not in a team!").color(NamedTextColor.RED));
                        return;
                    }

                    if (args.get(0) instanceof Player target) {
                        if (target.getName().equalsIgnoreCase(player.getName())) {
                            player.sendMessage(Component.text("You cannot invite yourself.").color(NamedTextColor.RED));
                            return;
                        }

                        if (team.getInvitations().contains(target.getUniqueId())) {
                            player.sendMessage(Component.text("You've already invited that player!").color(NamedTextColor.RED));
                            return;
                        }

                        if (team.getMembers().size() >= plugin.config().getInt("max_team_size", 5)) {
                            player.sendMessage(Component.text("Your team is already full!").color(NamedTextColor.RED));
                            return;
                        }

                        TeamInviteEvent teamInvite = new TeamInviteEvent(team, target);
                        if (teamInvite.callEvent()) {
                            team.addInvitation(target.getUniqueId());
                            player.sendMessage(MiniMessageUtil.deserialize(String.format("<yellow>%s</yellow> <gray>has been invited to your team!", target.getName())));

                            target.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><yellow>INVITE PENDING</yellow>\n  <reset>You've been invited to join <yellow>%s's</yellow> team!\n  You can type \"/team join %s\" to join their team!\n  <reset><yellow><bold>CLICK TO JOIN TEAM", player.getName(), player.getName()))
                                    .hoverEvent(HoverEvent.showText(Component.text(String.format("Click to join %s's team!", player.getName()))))
                                    .clickEvent(ClickEvent.runCommand(String.format("/team join %s", player.getName()))));
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("create")
                .withPermission(plugin.config().getString("commands.create", null))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team == null;
                    }

                    return false;
                })
                .executesPlayer((player, args) -> {
                    try {
                        if (this.cooldowns.containsKey(player.getUniqueId()) && this.cooldowns.get(player.getUniqueId()) - System.currentTimeMillis() > 0) {
                            long cooldownFor = this.cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
                            player.sendMessage(MiniMessageUtil.deserialize("<red>You are on cooldown for %s! Try again later.", StringUtil.getTimeString(cooldownFor)));
                            return;
                        }

                        if (plugin.getTeams().size() >= plugin.config().getInt("max_teams", 100)) {
                            player.sendMessage(Component.text("The maximum amount of teams has been reached!").color(NamedTextColor.RED));
                            return;
                        }

                        ITeam team = plugin.getTeam(player.getUniqueId());
                        if (team != null) {
                            player.sendMessage(Component.text("You are already in a team!").color(NamedTextColor.RED));
                            return;
                        }

                        TeamCreateByPlayerEvent teamCreateByPlayerEvent = new TeamCreateByPlayerEvent(team, player);
                        if (teamCreateByPlayerEvent.callEvent()) {
                            team = plugin.createTeamByType(player, EnumUtil.fetchEnum(GeneratorType.class, plugin.random().getString("type"), GeneratorType.NUMBER));
                            team.addMember(new TeamMember(player));

                            player.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><green>TEAM CREATED\n  <reset><gray>You've created Team %s\n ", team.getId())));
                            if (plugin.config().getBoolean("automatic_updates", true))
                                plugin.update();

                            CommandAPI.updateRequirements(player);
                        }
                    } catch (Exception | TeamAlreadyExistsException err) {
                        err.printStackTrace();
                        player.sendMessage(MiniMessageUtil.deserialize("<red><bold>ERROR! An unexpected error has occurred! | %s", err.toString()));
                    }
                })
        );
        withSubcommand(new CommandAPICommand("reset")
                .withPermission("lodestone.lead.commands.reset")
                .executesPlayer((player, args) -> {
                    if (teamReset.contains(player.getUniqueId())) {
                        teamReset.remove(player.getUniqueId());

                        plugin.teams().get().getKeys(false).forEach(key -> plugin.teams().set(key, null));
                        plugin.teams().save();
                        plugin.reload(true);

                        player.sendMessage(MiniMessageUtil.deserialize("<green>All teams have been reset!"));
                    } else {
                        player.sendMessage(MiniMessageUtil.deserialize("<red>Are you sure you want to reset all teams?"));
                        player.sendMessage(MiniMessageUtil.deserialize("<red>Run this command again to confirm!"));
                        teamReset.add(player.getUniqueId());

                        Task.later(plugin, () -> teamReset.remove(player.getUniqueId()), 20 * 5);
                    }
                })
        );
        withSubcommand(new CommandAPICommand("join")
                .withPermission(plugin.config().getString("commands.join", null))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team == null;
                    }

                    return false;
                })
                .withArguments(new OfflinePlayerArgument("target"))
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team != null) {
                        player.sendMessage(Component.text("You are already in a team!").color(NamedTextColor.RED));
                        return;
                    }

                    if (args.get(0) instanceof Player target) {
                        if (target.getName().equalsIgnoreCase(player.getName())) {
                            player.sendMessage(Component.text("You cannot join yourself.").color(NamedTextColor.RED));
                            return;
                        }

                        ITeam targetTeam = plugin.getTeam(target.getUniqueId());
                        if (targetTeam == null) {
                            player.sendMessage(Component.text("That player doesn't have a team!").color(NamedTextColor.RED));
                            return;
                        }

                        if (!targetTeam.getInvitations().contains(player.getUniqueId())) {
                            player.sendMessage(Component.text("That team has not sent you an invite!").color(NamedTextColor.RED));
                            return;
                        }

                        if (targetTeam.getMembers().size() >= plugin.config().getInt("max_team_size", 5)) {
                            player.sendMessage(Component.text("That team is already full!").color(NamedTextColor.RED));
                            return;
                        }

                        TeamJoinEvent joinEvent = new TeamJoinEvent(targetTeam, player);
                        if (joinEvent.callEvent()) {
                            targetTeam.removeInvitation(player.getUniqueId());
                            targetTeam.addMember(new TeamMember(player));

                            for (UUID playerUniqueId : targetTeam.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                Player p = plugin.getServer().getPlayer(playerUniqueId);
                                if (p != null)
                                    p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><green>PLAYER JOINED\n  <reset><yellow>%s</yellow> <gray>has joined your team!\n ", player.getName())));
                            }

                            if (plugin.config().getBoolean("automatic_updates", true))
                                plugin.update();

                            CommandAPI.updateRequirements(player);
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("teleport")
                .withPermission(plugin.config().getString("commands.teleport", null))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(a -> plugin.getTeams().stream().map(ITeam::getId).map(String::valueOf).toArray(String[]::new))))
                .withArguments(new EntitySelectorArgument.OneEntity("target"))
                .executes((sender, args) -> {
                    Entity target = (Entity) args.get("target");
                    String name = (String) args.get("team_id");
                    assert target != null;
                    assert name != null;

                    ITeam team = plugin.getTeam(name);
                    if (team == null) {
                        sender.sendMessage(MiniMessageUtil.deserialize("<red>That team doesn't exist!"));
                        return;
                    }

                    TeamTeleportEvent teamTeleportEvent = new TeamTeleportEvent(team, target);
                    if (teamTeleportEvent.callEvent()) {
                        int c = 0;
                        for (ITeamMember member : team.getMembers()) {
                            Player player = plugin.getServer().getPlayer(member.getUniqueId());
                            if (player == null) continue;

                            player.teleport(target.getLocation());
                            c++;
                        }

                        if (c > 0)
                            sender.sendMessage(MiniMessageUtil.deserialize("Teleported <%s>Team %s <reset>to %s", team.getColor(), team.getId(), target.getName()));
                        else
                            sender.sendMessage(MiniMessageUtil.deserialize("<red>No members of that team are online!"));
                    }
                })
        );
        withSubcommand(new CommandAPICommand("chat")
                .withPermission(plugin.config().getString("commands.chat", null))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team != null;
                    }

                    return false;
                })
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team == null) {
                        player.sendMessage(Component.text("You are not in a team!").color(NamedTextColor.RED));
                        return;
                    }

                    ITeamMember teamMember = team.getMember(player.getUniqueId());
                    assert teamMember != null;

                    teamMember.setInTeamChat(!teamMember.isInTeamChat());
                    player.sendMessage(MiniMessageUtil.deserialize(teamMember.isInTeamChat() ? "<green>You are now in team chat!" : "<red>You are no longer in team chat!"));
                })
        );
        withSubcommand(new CommandAPICommand("merge")
                .withPermission(plugin.config().getString("commands.merge", null))
                .withArguments(new StringArgument("team_one").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("team_two").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof String teamOneName) {
                        if (args.get(1) instanceof String teamTwoName) {
                            ITeam teamOne, teamTwo;
                            teamOne = plugin.getTeam(teamOneName);
                            if (teamOne == null) {
                                sender.sendMessage(Component.text("Team #1 doesn't exist!").color(NamedTextColor.RED));
                                return;
                            }

                            teamTwo = plugin.getTeam(teamTwoName);
                            if (teamTwo == null) {
                                sender.sendMessage(Component.text("Team #2 doesn't exist!").color(NamedTextColor.RED));
                                return;
                            }

                            if (Objects.equals(teamOne.getId(), teamTwo.getId())) {
                                sender.sendMessage(MiniMessageUtil.deserialize("<red>You cannot merge two of the same teams!"));
                                return;
                            }

                            TeamMergeEvent mergeEvent = new TeamMergeEvent(teamOne, teamTwo);
                            if (mergeEvent.callEvent()) {
                                List<ITeamMember> originalMembers = new ArrayList<>(teamOne.getMembers());

                                for (ITeamMember teamMember : teamTwo.getMembers()) {
                                    teamOne.addMember(new TeamMember(teamMember.getUniqueId(), teamMember.getName()));
                                    teamTwo.removeMember(teamMember.getUniqueId());

                                    for (UUID playerUniqueId : originalMembers.stream().map(ITeamMember::getUniqueId).toList()) {
                                        Player p = plugin.getServer().getPlayer(playerUniqueId);
                                        if (p != null)
                                            p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><green>PLAYER JOINED\n  <reset><yellow>%s</yellow> <gray>has joined your team!\n ", teamMember.getName())));
                                    }
                                }

                                plugin.deleteTeam(teamTwo);

                                sender.sendMessage(MiniMessageUtil.deserialize(String.format("Merged <yellow>%s <white>members to Team %s", teamTwo.getMembers().size(), teamOne.getId())));
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();

                                if (sender instanceof Player player)
                                    CommandAPI.updateRequirements(player);
                            }
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("spawn")
                .withPermission(plugin.config().getString("commands.spawn", null))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(a -> plugin.getTeams().stream().map(ITeam::getId).map(String::valueOf).toArray(String[]::new))))
                .withSubcommand(new CommandAPICommand("set")
                        .executes((sender, args) -> {
                            if (sender instanceof Player player) {
                                ITeam team = plugin.getTeam(player.getUniqueId());
                                if (team == null) {
                                    player.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                    return;
                                }

                                if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                                    player.sendMessage(Component.text("You are not the team leader!").color(NamedTextColor.RED));
                                    return;
                                }

                                TeamSetSpawnEvent teamSetSpawnEvent = new TeamSetSpawnEvent(team, player.getLocation());
                                if (teamSetSpawnEvent.callEvent()) {
                                    team.setSpawnLocation(player.getLocation());
                                    player.sendMessage(MiniMessageUtil.deserialize("<green>Successfully set your team's spawn location!"));
                                }
                            } else {
                                sender.sendMessage(MiniMessageUtil.deserialize("<red>You must be a player to run this command!"));
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("reset")
                        .executes((sender, args) -> {
                            if (sender instanceof Player player) {
                                ITeam team = plugin.getTeam(player.getUniqueId());
                                if (team == null) {
                                    player.sendMessage(Component.text("You are not in a team!").color(NamedTextColor.RED));
                                    return;
                                }

                                if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                                    player.sendMessage(Component.text("You are not the team leader!").color(NamedTextColor.RED));
                                    return;
                                }

                                TeamResetSpawnEvent teamResetSpawnEvent = new TeamResetSpawnEvent(team);
                                if (teamResetSpawnEvent.callEvent()) {
                                    team.setSpawnLocation(null);
                                    player.sendMessage(MiniMessageUtil.deserialize("<green>Successfully reset your team's spawn location!"));
                                }
                            } else {
                                sender.sendMessage(MiniMessageUtil.deserialize("<red>You must be a player to run this command!"));
                            }
                        }))
        );
        withSubcommand(new CommandAPICommand("shuffle")
                .withPermission(plugin.config().getString("commands.shuffle", null))
                .withOptionalArguments(new BooleanArgument("force"))
                .executes((sender, args) -> {
                    boolean shouldForce = args.get(0) instanceof Boolean force && force;
                    // Grab all online players, and assign them to a team
                    List<ITeam> teams = plugin.getTeams();
                    List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
                    for (ITeam team : teams) {
                        if (!shouldForce && team.getMembers().size() >= plugin.config().getInt("max_team_size", 5))
                            continue;

                        for (int i = 0; i < team.getMembers().size(); i++) {
                            if (players.isEmpty()) break;

                            Player player = players.remove(LeadPlugin.SEED.nextInt(players.size()));
                            ITeam targetTeam = plugin.getTeam(player.getUniqueId());
                            if (!shouldForce && targetTeam != null) continue;
                            if (targetTeam != null) targetTeam.removeMember(player.getUniqueId());
                            team.addMember(new TeamMember(player));
                            player.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><green>PLAYER JOINED\n  <reset><yellow>%s</yellow> <gray>has joined your team!\n ", player.getName())));
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("modify")
                .withRequirement((sender) -> {
                    if (sender == null) return false;

                    // does the sender have any of the permission that commands below require?
                    return sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.teleport", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.friendly_fire", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.nametag", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.merge", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.place", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.remove", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.delete", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.id", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.display_name", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.color", "lodestone.lead.manage"))) ||
                            sender.hasPermission(Objects.requireNonNull(plugin.config().getString("commands.collidable", "lodestone.lead.manage")));
                })
                .withSubcommand(new CommandAPICommand("color")
                        .withPermission(plugin.config().getString("commands.color", null))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("new_color"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                if (args.get(1) instanceof String newTeamColor) {
                                    ITeam team = plugin.getTeam(targetTeam);
                                    if (team == null) {
                                        sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                        return;
                                    }

                                    String oldTeamColor = team.getColor();
                                    TeamChangeColorEvent teamChangeColorEvent = new TeamChangeColorEvent(team, oldTeamColor, newTeamColor);
                                    if (teamChangeColorEvent.callEvent()) {
                                        if (!newTeamColor.matches("^#?([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
                                            sender.sendMessage(MiniMessageUtil.deserialize("That color isn't a valid hex color!").color(NamedTextColor.RED));
                                            return;
                                        }

                                        if (!newTeamColor.startsWith("#")) newTeamColor = "#" + newTeamColor;

                                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                                            if (p != null)
                                                p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>TEAM COLOR CHANGED\n  <reset><yellow>%s</yellow> <gray>has changed your team color to <%s>%s<gray>!\n ", sender.getName(), newTeamColor, newTeamColor)));
                                        }

                                        team.setColor(newTeamColor);
                                        sender.sendMessage(MiniMessageUtil.deserialize(String.format("Successfully changed <%s>Team %s<white>'s color to <%s>%s!", team.getColor(), team.getId(), newTeamColor, newTeamColor)));
                                        if (plugin.config().getBoolean("automatic_updates", true))
                                            plugin.update();

                                        if (sender instanceof Player player)
                                            CommandAPI.updateRequirements(player);
                                    }
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("display_name")
                        .withPermission(plugin.config().getString("commands.display_name", null))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new GreedyStringArgument("new_name"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                if (args.get(1) instanceof String newTeamName) {
                                    ITeam team = plugin.getTeam(targetTeam);
                                    if (team == null) {
                                        sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                        return;
                                    }

                                    String oldTeamName = team.getId();
                                    TeamChangeNameEvent teamChangeNameEvent = new TeamChangeNameEvent(team, oldTeamName, newTeamName);
                                    if (teamChangeNameEvent.callEvent()) {
                                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                                            if (p != null)
                                                p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>TEAM NAME CHANGED\n  <reset><yellow>%s</yellow> <gray>has changed your team name to <yellow>%s<gray>!\n ", sender.getName(), newTeamName)));
                                        }

                                        team.setName(newTeamName);
                                        sender.sendMessage(MiniMessageUtil.deserialize(String.format("Successfully changed Team %s's name from %s<white> to <yellow>%s!", team.getId(), oldTeamName, newTeamName)));
                                        plugin.update();

                                        if (sender instanceof Player player)
                                            CommandAPI.updateRequirements(player);
                                    }
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("id")
                        .withPermission(plugin.config().getString("commands.id", null))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("new_id"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                if (args.get(1) instanceof String newTeamId) {
                                    ITeam team = plugin.getTeam(targetTeam);
                                    if (team == null) {
                                        sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                        return;
                                    }

                                    String oldTeamId = team.getId();
                                    TeamChangeIdEvent teamChangeIdEvent = new TeamChangeIdEvent(team, oldTeamId, newTeamId);
                                    if (teamChangeIdEvent.callEvent()) {
                                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                                            if (p != null)
                                                p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>TEAM ID CHANGED\n  <reset><yellow>%s</yellow> <gray>has changed your team id to <yellow>%s<gray>!\n ", sender.getName(), newTeamId)));
                                        }

                                        team.setId(newTeamId);
                                        team.setName(newTeamId);
                                        sender.sendMessage(MiniMessageUtil.deserialize(String.format("Successfully changed the team with an id of %s<white> to <yellow>%s!", oldTeamId, newTeamId)));
                                        if (plugin.config().getBoolean("automatic_updates", true))
                                            plugin.update();

                                        if (sender instanceof Player player)
                                            CommandAPI.updateRequirements(player);
                                    }
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("collidable")
                        .withPermission(plugin.config().getString("commands.collidable", null))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("value").replaceSuggestions(ArgumentSuggestions.strings(s -> Arrays.stream(org.bukkit.scoreboard.Team.OptionStatus.values()).map(org.bukkit.scoreboard.Team.OptionStatus::name).toArray(String[]::new))))
                        .executes((sender, args) -> {
                            if (plugin.isTABPresent()) {
                                sender.sendMessage(MiniMessageUtil.deserialize("<red>This feature is not available with TAB!"));
                                return;
                            }

                            if (args.get(0) instanceof String targetTeam) {
                                ITeam team = plugin.getTeam(targetTeam);
                                if (team == null) {
                                    sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                    return;
                                }

                                if (args.get(1) instanceof String optionStatusName) {
                                    org.bukkit.scoreboard.Team.OptionStatus status = EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, optionStatusName);
                                    if (status == null) {
                                        sender.sendMessage(MiniMessageUtil.deserialize("<red>That option status isn't valid!"));
                                        return;
                                    }

                                    team.setCollidable(status);
                                    sender.sendMessage(MiniMessageUtil.deserialize("Collision rule for team \"%s\" is now \"%s\"", team.getId(), StringUtil.titleCase(status.name(), true)));
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("friendly_fire")
                        .withPermission(plugin.config().getString("commands.friendly_fire", null))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new BooleanArgument("value"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                ITeam team = plugin.getTeam(targetTeam);
                                if (team == null) {
                                    sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                    return;
                                }

                                if (args.get(1) instanceof Boolean value) {
                                    team.setFriendlyFireAllowed(value);
                                    sender.sendMessage(MiniMessageUtil.deserialize("%s friendly fire for team \"%s\"", value ? "Enabled" : "Disabled", team.getId()));
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("nametag")
                        .withPermission(plugin.config().getString("commands.nametag", null))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("value").replaceSuggestions(ArgumentSuggestions.strings(s -> Arrays.stream(org.bukkit.scoreboard.Team.OptionStatus.values()).map(org.bukkit.scoreboard.Team.OptionStatus::name).toArray(String[]::new))))
                        .executes((sender, args) -> {
                            if (plugin.isTABPresent()) {
                                sender.sendMessage(MiniMessageUtil.deserialize("<red>This feature is not available with TAB!"));
                                return;
                            }

                            if (args.get(0) instanceof String targetTeam) {
                                ITeam team = plugin.getTeam(targetTeam);
                                if (team == null) {
                                    sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                    return;
                                }

                                if (args.get(1) instanceof String optionStatusName) {
                                    org.bukkit.scoreboard.Team.OptionStatus status = EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, optionStatusName);
                                    if (status == null) {
                                        sender.sendMessage(MiniMessageUtil.deserialize("<red>That option status isn't valid!"));
                                        return;
                                    }

                                    team.setNameTagVisibility(status);
                                    sender.sendMessage(MiniMessageUtil.deserialize("Nametag visibility for team \"%s\" is now \"%s\"", team.getId(), StringUtil.titleCase(status.name(), true)));
                                    if (plugin.config().getBoolean("automatic_updates", true))
                                        plugin.update();
                                }
                            }
                        })
                )
        );
        withSubcommand(new CommandAPICommand("place")
                .withAliases("add")
                .withPermission(plugin.config().getString("commands.place", null))
                .withArguments(new PlayerArgument("target"), new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof Player target) {
                        if (args.get(1) instanceof String targetTeam) {
                            ITeam team = plugin.getTeam(targetTeam);
                            if (team == null) {
                                sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                                return;
                            }

                            if (team.containsMember(target.getUniqueId())) {
                                sender.sendMessage(MiniMessageUtil.deserialize("<red>%s is already in that team!", target.getName()));
                                return;
                            }

                            TeamJoinEvent joinEvent = new TeamJoinEvent(team, target);
                            if (joinEvent.callEvent()) {
                                ITeam previousTeam = plugin.getTeam(target.getUniqueId());
                                if (previousTeam != null) previousTeam.removeMember(target.getUniqueId());

                                team.addMember(new TeamMember(target.getUniqueId(), target.getName()));
                                for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                    Player p = plugin.getServer().getPlayer(playerUniqueId);
                                    if (p != null)
                                        p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><green>PLAYER JOINED\n  <reset><yellow>%s</yellow> <gray>has joined your team!\n ", target.getName())));
                                }

                                sender.sendMessage(MiniMessageUtil.deserialize(String.format("Added <yellow>%s <white>to <%s>Team %s", target.getName(), team.getColor(), team.getId())));
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();

                                if (sender instanceof Player player)
                                    CommandAPI.updateRequirements(player);
                            }
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("remove")
                .withPermission(plugin.config().getString("commands.remove", null))
                .withArguments(new OfflinePlayerArgument("target"))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof OfflinePlayer target) {
                        ITeam team = plugin.getTeam(target.getUniqueId());
                        if (team == null) {
                            sender.sendMessage(Component.text("That player doesn't have a team!").color(NamedTextColor.RED));
                            return;
                        }

                        TeamLeaveEvent leaveEvent = new TeamLeaveEvent(target, team);
                        if (leaveEvent.callEvent()) {
                            for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                Player p = plugin.getServer().getPlayer(playerUniqueId);
                                if (p != null)
                                    p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>PLAYER REMOVED\n  <reset><yellow>%s</yellow> <gray>has been removed from your team!\n ", target.getName())));
                            }

                            team.removeMember(target.getUniqueId());

                            sender.sendMessage(MiniMessageUtil.deserialize(String.format("Removed <yellow>%s <white>from <%s>Team %s", target.getName(), team.getColor(), team.getId())));
                            if (plugin.config().getBoolean("automatic_updates", true))
                                plugin.update();

                            if (sender instanceof Player player)
                                CommandAPI.updateRequirements(player);
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("delete")
                .withPermission(plugin.config().getString("commands.delete", null))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof String targetTeam) {
                        ITeam team = plugin.getTeam(targetTeam);
                        if (team == null) {
                            sender.sendMessage(Component.text("That team doesn't exist!").color(NamedTextColor.RED));
                            return;
                        }

                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                            if (p != null)
                                p.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <bold><red>TEAM DELETED\n  <reset><yellow>%s</yellow> <gray>has deleted your team!\n ", sender.getName())));
                        }

                        plugin.deleteTeam(team);
                        if (plugin.config().getBoolean("automatic_updates", true))
                            plugin.update();

                        if (sender instanceof Player player)
                            CommandAPI.updateRequirements(player);

                        sender.sendMessage(MiniMessageUtil.deserialize(String.format("Successfully deleted <%s>Team %s<white>!", team.getColor(), team.getId())));
                    }
                })
        );
        withSubcommand(new CommandAPICommand("list")
                .withPermission(plugin.config().getString("commands.list", null))
                .executesPlayer((player, args) -> {
                    if (plugin.getTeams().size() == 0) {
                        player.sendMessage(MiniMessageUtil.deserialize("<red>There are no teams to display!"));
                        return;
                    }

                    new TeamListMenu(plugin, player, 0).open();
                }));
        withSubcommand(new CommandAPICommand("help")
                .withPermission(plugin.config().getString("commands.help", null))
                .executes(this::executeHelpCommand));
        executes(this::executeHelpCommand);
    }

    private void executeHelpCommand(CommandSender sender, CommandArguments args) {
        sender.sendMessage(MiniMessageUtil.deserialize(" "));
        sender.sendMessage(MiniMessageUtil.deserialize("<bold>Team Commands"));
        sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team create><hover:show_text:\"<yellow>Click to create a team!\">/team create</hover></click>"));
        sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team join>/team join</click></yellow>"));
        sender.sendMessage(MiniMessageUtil.deserialize("To view the full list of teams, you can use <yellow><click:run_command:/team list><hover:show_text:\"<yellow>Click to view the list of teams!\">/team list</hover></click>"));
        sender.sendMessage(MiniMessageUtil.deserialize(" "));
        sender.sendMessage(MiniMessageUtil.deserialize("<bold>Member Commands"));
        sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team leave><hover:show_text:\"<yellow>Click to leave your team!\">/team leave</hover></click></yellow>"));
        sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team invite>/team invite</click></yellow>"));
        sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/tc>/tc <msg></click></yellow> or use <yellow><click:suggest_command:/team chat>/team chat</click></yellow> to toggle."));
        sender.sendMessage(MiniMessageUtil.deserialize(" "));
        sender.sendMessage(MiniMessageUtil.deserialize("<bold>Leader Commands"));
        sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team kick>/team click</click></yellow>"));
        sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team disband><hover:show_text:\"<yellow>Click to disband your team!\">/team disband</hover></click>"));
        if (sender.hasPermission("lodestone.lead.manage")) {
            sender.sendMessage(MiniMessageUtil.deserialize(" "));
            sender.sendMessage(MiniMessageUtil.deserialize("<bold>Admin Commands"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team merge>/team merge <team_one_id> <team_two_id></click></yellow>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team place>/team place <player> <team_id></click>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team remove>/team remove <player></click>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team delete>/team delete <team_id></click>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team modify color>/team modify color <team_id> <new_color></click>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team modify display_name>/team modify display_name <team_id> <display_name></click>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team modify collidable>/team modify collidable <team_id> <value></click>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team modify name_tag>/team modify name_tag <team_id> <value></click>"));
            sender.sendMessage(MiniMessageUtil.deserialize("- <yellow><click:suggest_command:/team modify friendly_fire>/team modify friendly_fire <team_id> <value></click>"));
        }
        sender.sendMessage(MiniMessageUtil.deserialize(" "));
    }
}
