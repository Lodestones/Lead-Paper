package gg.lode.lead.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandArguments;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.util.EnumHelper;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.bookshelfapi.api.util.StringHelper;
import gg.lode.bookshelfapi.api.util.VariableContext;
import gg.lode.bookshelfcmd.util.CommandHelper;
import gg.lode.lead.LeadPlugin;
import gg.lode.lead.menu.TeamEditorMenu;
import gg.lode.lead.menu.TeamListMenu;
import gg.lode.lead.team.TeamMember;
import gg.lode.leadapi.api.GeneratorType;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import gg.lode.leadapi.api.event.*;
import gg.lode.leadapi.api.exception.TeamAlreadyExistsException;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TeamCommand extends CommandAPICommand {
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final List<UUID> teamReset = new ArrayList<>();
    private final LeadPlugin plugin;

    public TeamCommand(LeadPlugin plugin) {
        super("team");
        this.plugin = plugin;
        @Nullable String commandPermission = plugin.config().getString("permissions.team");
        if (commandPermission != null) withPermission(convertPermission(commandPermission));
        withAliases("t");
        withSubcommand(new CommandAPICommand("kick")
                .withPermission(convertPermission(plugin.config().getString("commands.kick", null)))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team != null && team.getLeaderUniqueId() != null && team.containsMember(team.getLeaderUniqueId()) && team.getLeaderUniqueId().toString().equalsIgnoreCase(player.getUniqueId().toString());
                    }

                    return false;
                })
                .withArguments(new PlayerProfileArgument("target"))
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team == null) {
                        player.sendMessage(plugin.message(player, "lead.command.kick.no_team"));
                        return;
                    }

                    if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(plugin.message(player, "lead.command.not_leader"));
                        return;
                    }

                    if (args.get(0) instanceof List<?> l) {
                        OfflinePlayer target = CommandHelper.convertPlayerProfileToOfflinePlayer((List<PlayerProfile>) l);

                        if (target == null || target.getName() == null) {
                            player.sendMessage(plugin.message(player, "lead.command.invite.player_not_online"));
                            return;
                        }

                        if (target.getName().equalsIgnoreCase(player.getName())) {
                            player.sendMessage(plugin.message(player, "lead.command.cannot_kick_yourself"));
                            return;
                        }

                        TeamKickEvent kickEvent = new TeamKickEvent(team, target);
                        if (kickEvent.callEvent()) {
                            for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                Player p = plugin.getServer().getPlayer(playerUniqueId);
                                if (p != null)
                                    p.sendMessage(plugin.message(p, "lead.command.player_kicked", VariableContext.of("player", target.getName())));
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
                .withPermission(convertPermission(plugin.config().getString("commands.leave", null)))
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
                        player.sendMessage(plugin.message(player, "lead.command.kick.no_team"));
                        return;
                    }

                    if (team.getLeaderUniqueId() != null && team.getLeaderUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(plugin.message(player, "lead.command.leave.is_leader"));
                        return;
                    }

                    TeamLeaveEvent teamLeaveEvent = new TeamLeaveEvent(player, team);
                    if (teamLeaveEvent.callEvent()) {
                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                            if (p != null)
                                p.sendMessage(plugin.message(p, "lead.command.player_left", VariableContext.of("player", player.getName())));
                        }

                        team.removeMember(player.getUniqueId());
                        if (plugin.config().getBoolean("automatic_updates", true))
                            plugin.update();

                        CommandAPI.updateRequirements(player);
                    }
                })
        );
        withSubcommand(new CommandAPICommand("disband")
                .withPermission(convertPermission(plugin.config().getString("commands.disband", null)))
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
                        player.sendMessage(plugin.message(player, "lead.command.kick.no_team"));
                        return;
                    }

                    if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(plugin.message(player, "lead.command.not_leader"));
                        return;
                    }

                    TeamDisbandEvent disbandEvent = new TeamDisbandEvent(team);
                    if (disbandEvent.callEvent()) {
                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                            if (p != null)
                                p.sendMessage(plugin.message(p, "lead.command.team_disbanded", VariableContext.of("player", player.getName())));
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
                .withPermission(convertPermission(plugin.config().getString("commands.edit", "lodestone.lead.manage")))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executesPlayer((player, args) -> {
                    if (args.get(0) instanceof String id) {
                        ITeam team = plugin.getTeam(id);
                        if (team == null) {
                            player.sendMessage(plugin.message(player, "lead.command.edit.team_not_found"));
                            return;
                        }

                        new TeamEditorMenu(plugin, player, team).open();
                    }
                })
        );
        withSubcommand(new CommandAPICommand("invite")
                .withPermission(convertPermission(plugin.config().getString("commands.invite", null)))
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
                        player.sendMessage(plugin.message(player, "lead.command.kick.no_team"));
                        return;
                    }

                    if (args.get(0) instanceof Player target) {
                        if (target.getName().equalsIgnoreCase(player.getName())) {
                            player.sendMessage(plugin.message(player, "lead.command.cannot_invite_yourself"));
                            return;
                        }

                        if (team.getInvitations().contains(target.getUniqueId())) {
                            player.sendMessage(plugin.message(player, "lead.command.invite.already_invited"));
                            return;
                        }

                        if (team.getMembers().size() >= plugin.config().getInt("max_team_size", 5)) {
                            player.sendMessage(plugin.message(player, "lead.command.invite.team_full"));
                            return;
                        }

                        TeamInviteEvent teamInvite = new TeamInviteEvent(team, player, target);
                        if (teamInvite.callEvent()) {
                            team.addInvitation(target.getUniqueId());
                            player.sendMessage(plugin.message(player, "lead.command.invite.sent", VariableContext.of("player", target.getName())));

                            target.sendMessage(plugin.message(target, "lead.command.invite.received", VariableContext.of("leader", player.getName()))
                                    .hoverEvent(HoverEvent.showText(plugin.message(target, "lead.command.invite.hover", VariableContext.of("leader", player.getName()))))
                                    .clickEvent(ClickEvent.runCommand(String.format("/team join %s", player.getName()))));
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("create")
                .withPermission(convertPermission(plugin.config().getString("commands.create", null)))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team == null;
                    }

                    return false;
                })
                .withOptionalArguments(new GreedyStringArgument("name").withRequirement(sender -> plugin.config().getBoolean("nameable_teams")))
                .executesPlayer((player, args) -> {
                    try {
                        if (this.cooldowns.containsKey(player.getUniqueId()) && this.cooldowns.get(player.getUniqueId()) - System.currentTimeMillis() > 0) {
                            long cooldownFor = this.cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
                            player.sendMessage(plugin.message(player, "lead.command.cooldown", VariableContext.of("duration", StringHelper.getTimeString(cooldownFor))));
                            return;
                        }

                        if (plugin.getTeams().size() >= plugin.config().getInt("max_teams", 100)) {
                            player.sendMessage(plugin.message(player, "lead.command.create.max_teams_reached"));
                            return;
                        }

                        ITeam team = plugin.getTeam(player.getUniqueId());
                        if (team != null) {
                            player.sendMessage(plugin.message(player, "lead.command.already_in_team"));
                            return;
                        }

                        boolean namableTeams = plugin.config().getBoolean("nameable_teams");
                        if (namableTeams) {
                            if (args.get(0) instanceof String teamName) {
                                int minimumTeamLength = plugin.config().getInt("minimum_team_name_length", 3);
                                if (teamName.length() < minimumTeamLength) {
                                    player.sendMessage(plugin.message(player, "lead.command.create.name_too_short", VariableContext.of("min", String.valueOf(minimumTeamLength))));
                                    return;
                                }

                                int maximumTeamLength = plugin.config().getInt("maximum_team_name_length", 16);
                                if (teamName.length() > maximumTeamLength) {
                                    player.sendMessage(plugin.message(player, "lead.command.create.name_too_long", VariableContext.of("max", String.valueOf(maximumTeamLength))));
                                    return;
                                }

                                if (!teamName.matches("[ 0-9a-zA-Z_+-]+")) {
                                    player.sendMessage(plugin.message(player, "lead.command.create.invalid_characters"));
                                    return;
                                }

                                TeamCreateByPlayerEvent teamCreateByPlayerEvent = new TeamCreateByPlayerEvent(team, player);
                                if (teamCreateByPlayerEvent.callEvent()) {
                                    team = plugin.createTeamByType(player, teamName, GeneratorType.NAME);
                                    team.setId(teamName.toUpperCase().replaceAll(" ", "_"));
                                    team.addMember(new TeamMember(player));

                                    player.sendMessage(plugin.message(player, "lead.command.create.named_success", VariableContext.of("name", teamName)));
                                    if (plugin.config().getBoolean("automatic_updates", true))
                                        plugin.update();

                                    CommandAPI.updateRequirements(player);
                                }
                            } else {
                                player.sendMessage(plugin.message(player, "lead.command.create.name_required"));
                            }
                        } else {
                            TeamCreateByPlayerEvent teamCreateByPlayerEvent = new TeamCreateByPlayerEvent(team, player);
                            if (teamCreateByPlayerEvent.callEvent()) {
                                team = plugin.createTeamByType(player, EnumHelper.fetchEnum(GeneratorType.class, plugin.random().getString("type"), GeneratorType.NUMBER));
                                team.addMember(new TeamMember(player));

                                player.sendMessage(plugin.message(player, "lead.command.create.unnamed_success", VariableContext.of("id", team.getId())));
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();

                                CommandAPI.updateRequirements(player);
                            }
                        }
                    } catch (Exception | TeamAlreadyExistsException err) {
                        err.printStackTrace();
                        player.sendMessage(plugin.message(player, "lead.command.error", VariableContext.of("error", err.toString())));
                    }
                })
        );
        withSubcommand(new CommandAPICommand("reset")
                .withAliases("clear")
                .withPermission(convertPermission("lodestone.lead.commands.reset"))
                .executesPlayer((player, args) -> {
                    if (teamReset.contains(player.getUniqueId())) {
                        teamReset.remove(player.getUniqueId());

                        plugin.teams().get().getKeys(false).forEach(key -> plugin.teams().set(key, null));
                        plugin.teams().save();
                        plugin.reload(true);

                        player.sendMessage(plugin.message(player, "lead.command.disband.all_teams_reset"));
                    } else {
                        player.sendMessage(plugin.message(player, "lead.command.disband.confirmation_needed"));
                        player.sendMessage(plugin.message(player, "lead.command.disband.run_again"));
                        teamReset.add(player.getUniqueId());

                        Task.later(plugin, () -> teamReset.remove(player.getUniqueId()), 20 * 5);
                    }
                })
        );
        withSubcommand(new CommandAPICommand("join")
                .withPermission(convertPermission(plugin.config().getString("commands.join", null)))
                .withRequirement(sender -> {
                    if (!plugin.config().getBoolean("is_public") && !sender.hasPermission("lodestone.lead.manage"))
                        return false;

                    if (sender instanceof Player player) {
                        ITeam team = plugin.getTeam(player.getUniqueId());
                        return team == null;
                    }

                    return false;
                })
                .withArguments(new PlayerProfileArgument("target"))
                .executesPlayer((player, args) -> {
                    ITeam team = plugin.getTeam(player.getUniqueId());
                    if (team != null) {
                        player.sendMessage(plugin.message(player, "lead.command.already_in_team"));
                        return;
                    }

                    if (args.get(0) instanceof List<?> l) {
                        OfflinePlayer target = CommandHelper.convertPlayerProfileToOfflinePlayer((List<PlayerProfile>) l);
                        if (target == null || target.getName() == null) {
                            player.sendMessage(plugin.message(player, "lead.command.invite.player_not_online"));
                            return;
                        }

                        if (target.getName().equalsIgnoreCase(player.getName())) {
                            player.sendMessage(plugin.message(player, "lead.command.cannot_join_yourself"));
                            return;
                        }

                        ITeam targetTeam = plugin.getTeam(target.getUniqueId());
                        if (targetTeam == null) {
                            player.sendMessage(plugin.message(player, "lead.command.join.team_not_found"));
                            return;
                        }

                        if (!targetTeam.getInvitations().contains(player.getUniqueId())) {
                            player.sendMessage(plugin.message(player, "lead.command.join.invite_not_found"));
                            return;
                        }

                        if (targetTeam.getMembers().size() >= plugin.config().getInt("max_team_size", 5)) {
                            player.sendMessage(plugin.message(player, "lead.command.join.team_full"));
                            return;
                        }

                        TeamJoinEvent joinEvent = new TeamJoinEvent(targetTeam, player);
                        if (joinEvent.callEvent()) {
                            targetTeam.removeInvitation(player.getUniqueId());
                            targetTeam.addMember(new TeamMember(player));

                            for (UUID playerUniqueId : targetTeam.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                Player p = plugin.getServer().getPlayer(playerUniqueId);
                                if (p != null)
                                    p.sendMessage(plugin.message(p, "lead.command.player_joined", VariableContext.of("player", player.getName())));
                            }

                            if (plugin.config().getBoolean("automatic_updates", true))
                                plugin.update();

                            CommandAPI.updateRequirements(player);
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("teleport")
                .withPermission(convertPermission(plugin.config().getString("commands.teleport", null)))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(a -> plugin.getTeams().stream().map(ITeam::getId).map(String::valueOf).toArray(String[]::new))))
                .withArguments(new EntitySelectorArgument.OneEntity("target"))
                .executes((sender, args) -> {
                    Entity target = (Entity) args.get("target");
                    String name = (String) args.get("team_id");
                    assert target != null;
                    assert name != null;

                    ITeam team = plugin.getTeam(name);
                    if (team == null) {
                        sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
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
                            sender.sendMessage(plugin.message(sender, "lead.command.place.success", VariableContext.of("color", team.getColor()).with("name", team.getId()).with("location", target.getName())));
                        else
                            sender.sendMessage(plugin.message(sender, "lead.command.no_online_members"));
                    }
                })
        );
        withSubcommand(new CommandAPICommand("chat")
                .withPermission(convertPermission(plugin.config().getString("commands.chat", null)))
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
                        player.sendMessage(plugin.message(player, "lead.command.kick.no_team"));
                        return;
                    }

                    ITeamMember teamMember = team.getMember(player.getUniqueId());
                    assert teamMember != null;

                    teamMember.setInTeamChat(!teamMember.isInTeamChat());
                    player.sendMessage(plugin.message(player, teamMember.isInTeamChat() ? "lead.command.chat.enabled" : "lead.command.chat.disabled"));
                })
        );
        withSubcommand(new CommandAPICommand("merge")
                .withPermission(convertPermission(plugin.config().getString("commands.merge", null)))
                .withArguments(new StringArgument("team_one").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("team_two").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof String teamOneName) {
                        if (args.get(1) instanceof String teamTwoName) {
                            ITeam teamOne, teamTwo;
                            teamOne = plugin.getTeam(teamOneName);
                            if (teamOne == null) {
                                sender.sendMessage(plugin.message(sender, "lead.command.team_not_found_1"));
                                return;
                            }

                            teamTwo = plugin.getTeam(teamTwoName);
                            if (teamTwo == null) {
                                sender.sendMessage(plugin.message(sender, "lead.command.team_not_found_2"));
                                return;
                            }

                            if (Objects.equals(teamOne.getId(), teamTwo.getId())) {
                                sender.sendMessage(plugin.message(sender, "lead.command.merge.cannot_merge_same"));
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
                                            p.sendMessage(plugin.message(p, "lead.command.player_joined", VariableContext.of("player", teamMember.getName())));
                                    }
                                }

                                plugin.deleteTeam(teamTwo);

                                sender.sendMessage(plugin.message(sender, "lead.command.merge.success", VariableContext.of("amount", String.valueOf(teamTwo.getMembers().size())).with("name", teamOne.getId())));
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
                .withPermission(convertPermission(plugin.config().getString("commands.spawn", null)))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(a -> plugin.getTeams().stream().map(ITeam::getId).map(String::valueOf).toArray(String[]::new))))
                .withSubcommand(new CommandAPICommand("set")
                        .executes((sender, args) -> {
                            if (sender instanceof Player player) {
                                ITeam team = plugin.getTeam(player.getUniqueId());
                                if (team == null) {
                                    player.sendMessage(plugin.message(player, "lead.command.edit.team_not_found"));
                                    return;
                                }

                                if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                                    player.sendMessage(plugin.message(player, "lead.command.not_leader"));
                                    return;
                                }

                                TeamSetSpawnEvent teamSetSpawnEvent = new TeamSetSpawnEvent(team, player.getLocation());
                                if (teamSetSpawnEvent.callEvent()) {
                                    team.setSpawnLocation(player.getLocation());
                                    player.sendMessage(plugin.message(player, "lead.menu.spawn.set_success"));
                                }
                            } else {
                                sender.sendMessage(plugin.message(sender, "lead.command.must_be_player"));
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("reset")
                        .executes((sender, args) -> {
                            if (sender instanceof Player player) {
                                ITeam team = plugin.getTeam(player.getUniqueId());
                                if (team == null) {
                                    player.sendMessage(plugin.message(player, "lead.command.kick.no_team"));
                                    return;
                                }

                                if (team.getLeaderUniqueId() != null && !team.getLeaderUniqueId().equals(player.getUniqueId())) {
                                    player.sendMessage(plugin.message(player, "lead.command.not_leader"));
                                    return;
                                }

                                TeamResetSpawnEvent teamResetSpawnEvent = new TeamResetSpawnEvent(team);
                                if (teamResetSpawnEvent.callEvent()) {
                                    team.setSpawnLocation(null);
                                    player.sendMessage(plugin.message(player, "lead.menu.spawn.reset_success"));
                                }
                            } else {
                                sender.sendMessage(plugin.message(sender, "lead.command.must_be_player"));
                            }
                        }))
        );
        withSubcommand(new CommandAPICommand("shuffle")
                .withPermission(convertPermission(plugin.config().getString("commands.shuffle", null)))
                .withOptionalArguments(new BooleanArgument("force"))
                .executes((sender, args) -> {
                    boolean shouldForce = args.get(0) instanceof Boolean force && force;
                    // Grab all online players, and assign them to a team
                    List<ITeam> teams = plugin.getTeams();
                    List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
                    Collections.shuffle(players, LeadPlugin.SEED); // Shuffle players

                    int teamCount = teams.size();
                    int playerIndex = 0;

                    for (Player player : players) {
                        // Find the next eligible team
                        for (int offset = 0; offset < teamCount; offset++) {
                            int teamIdx = (playerIndex + offset) % teamCount;
                            ITeam team = teams.get(teamIdx);
                            if (!shouldForce && team.getMembers().size() >= plugin.config().getInt("max_team_size", 5)) {
                                continue;
                            }
                            ITeam targetTeam = plugin.getTeam(player.getUniqueId());
                            if (!shouldForce && targetTeam != null) break;
                            if (targetTeam != null) targetTeam.removeMember(player.getUniqueId());
                            team.addMember(new TeamMember(player));
                            player.sendMessage(plugin.message(player, "lead.command.player_joined", VariableContext.of("player", player.getName())));
                            playerIndex = (teamIdx + 1) % teamCount;
                            break;
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
                        .withPermission(convertPermission(plugin.config().getString("commands.color", null)))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("new_color"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                if (args.get(1) instanceof String newTeamColor) {
                                    ITeam team = plugin.getTeam(targetTeam);
                                    if (team == null) {
                                        sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                                        return;
                                    }

                                    String oldTeamColor = team.getColor();
                                    TeamChangeColorEvent teamChangeColorEvent = new TeamChangeColorEvent(team, oldTeamColor, newTeamColor);
                                    if (teamChangeColorEvent.callEvent()) {
                                        if (!newTeamColor.matches("^#?([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
                                            sender.sendMessage(plugin.message(sender, "lead.command.modify_color.invalid"));
                                            return;
                                        }

                                        if (!newTeamColor.startsWith("#")) newTeamColor = "#" + newTeamColor;

                                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                                            if (p != null)
                                                p.sendMessage(plugin.message(p, "lead.command.modify_color.broadcast", VariableContext.of("player", sender.getName()).with("color", newTeamColor).with("color", newTeamColor)));
                                        }

                                        team.setColor(newTeamColor);
                                        sender.sendMessage(plugin.message(sender, "lead.command.modify_color.success", VariableContext.of("color", team.getColor()).with("name", team.getId()).with("new_color", newTeamColor).with("new_color", newTeamColor)));
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
                        .withPermission(convertPermission(plugin.config().getString("commands.display_name", null)))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new GreedyStringArgument("new_name"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                if (args.get(1) instanceof String newTeamName) {
                                    ITeam team = plugin.getTeam(targetTeam);
                                    if (team == null) {
                                        sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                                        return;
                                    }

                                    String oldTeamName = team.getId();
                                    TeamChangeNameEvent teamChangeNameEvent = new TeamChangeNameEvent(team, oldTeamName, newTeamName);
                                    if (teamChangeNameEvent.callEvent()) {
                                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                                            if (p != null)
                                                p.sendMessage(plugin.message(p, "lead.command.modify_name.broadcast", VariableContext.of("player", sender.getName()).with("name", newTeamName)));
                                        }

                                        team.setName(newTeamName);
                                        sender.sendMessage(plugin.message(sender, "lead.command.modify_name.success", VariableContext.of("id", team.getId()).with("old_name", oldTeamName).with("new_name", newTeamName)));
                                        plugin.update();

                                        if (sender instanceof Player player)
                                            CommandAPI.updateRequirements(player);
                                    }
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("id")
                        .withPermission(convertPermission(plugin.config().getString("commands.id", null)))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("new_id"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                if (args.get(1) instanceof String newTeamId) {
                                    ITeam team = plugin.getTeam(targetTeam);
                                    if (team == null) {
                                        sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                                        return;
                                    }

                                    String oldTeamId = team.getId();
                                    TeamChangeIdEvent teamChangeIdEvent = new TeamChangeIdEvent(team, oldTeamId, newTeamId);
                                    if (teamChangeIdEvent.callEvent()) {
                                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                                            if (p != null)
                                                p.sendMessage(plugin.message(p, "lead.command.modify_id.broadcast", VariableContext.of("player", sender.getName()).with("id", newTeamId)));
                                        }

                                        team.setId(newTeamId);
                                        team.setName(newTeamId);
                                        sender.sendMessage(plugin.message(sender, "lead.command.modify_id.success", VariableContext.of("old_id", oldTeamId).with("new_id", newTeamId)));
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
                        .withPermission(convertPermission(plugin.config().getString("commands.collidable", null)))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("value").replaceSuggestions(ArgumentSuggestions.strings(s -> Arrays.stream(org.bukkit.scoreboard.Team.OptionStatus.values()).map(org.bukkit.scoreboard.Team.OptionStatus::name).toArray(String[]::new))))
                        .executes((sender, args) -> {
                            if (plugin.isTABPresent()) {
                                sender.sendMessage(plugin.message(sender, "lead.command.tab_not_available"));
                                return;
                            }

                            if (args.get(0) instanceof String targetTeam) {
                                ITeam team = plugin.getTeam(targetTeam);
                                if (team == null) {
                                    sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                                    return;
                                }

                                if (args.get(1) instanceof String optionStatusName) {
                                    org.bukkit.scoreboard.Team.OptionStatus status = EnumHelper.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, optionStatusName);
                                    if (status == null) {
                                        sender.sendMessage(plugin.message(sender, "lead.command.invalid_option_status"));
                                        return;
                                    }

                                    team.setCollidable(status);
                                    sender.sendMessage(plugin.message(sender, "lead.command.modify_collision.success", VariableContext.of("team_id", team.getId()).with("value", StringHelper.titleCase(status.name(), true))));
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("friendly_fire")
                        .withPermission(convertPermission(plugin.config().getString("commands.friendly_fire", null)))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new BooleanArgument("value"))
                        .executes((sender, args) -> {
                            if (args.get(0) instanceof String targetTeam) {
                                ITeam team = plugin.getTeam(targetTeam);
                                if (team == null) {
                                    sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                                    return;
                                }

                                if (args.get(1) instanceof Boolean value) {
                                    team.setFriendlyFireAllowed(value);
                                    sender.sendMessage(plugin.message(sender, "lead.command.modify_friendly_fire.success", VariableContext.of("state", value ? "Enabled" : "Disabled").with("team_id", team.getId())));
                                }
                            }
                        })
                )
                .withSubcommand(new CommandAPICommand("nametag")
                        .withPermission(convertPermission(plugin.config().getString("commands.nametag", null)))
                        .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))), new StringArgument("value").replaceSuggestions(ArgumentSuggestions.strings(s -> Arrays.stream(org.bukkit.scoreboard.Team.OptionStatus.values()).map(org.bukkit.scoreboard.Team.OptionStatus::name).toArray(String[]::new))))
                        .executes((sender, args) -> {
                            if (plugin.isTABPresent()) {
                                sender.sendMessage(plugin.message(sender, "lead.command.tab_not_available"));
                                return;
                            }

                            if (args.get(0) instanceof String targetTeam) {
                                ITeam team = plugin.getTeam(targetTeam);
                                if (team == null) {
                                    sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                                    return;
                                }

                                if (args.get(1) instanceof String optionStatusName) {
                                    org.bukkit.scoreboard.Team.OptionStatus status = EnumHelper.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, optionStatusName);
                                    if (status == null) {
                                        sender.sendMessage(plugin.message(sender, "lead.command.invalid_option_status"));
                                        return;
                                    }

                                    team.setNameTagVisibility(status);
                                    sender.sendMessage(plugin.message(sender, "lead.command.modify_nametag.success", VariableContext.of("team_id", team.getId()).with("value", StringHelper.titleCase(status.name(), true))));
                                    if (plugin.config().getBoolean("automatic_updates", true))
                                        plugin.update();
                                }
                            }
                        })
                )
        );
        withSubcommand(new CommandAPICommand("place")
                .withAliases("add")
                .withPermission(convertPermission(plugin.config().getString("commands.place", null)))
                .withArguments(new EntitySelectorArgument.OnePlayer("target"), new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof Player target) {
                        if (args.get(1) instanceof String targetTeam) {
                            ITeam team = plugin.getTeam(targetTeam);
                            if (team == null) {
                                sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                                return;
                            }

                            if (team.containsMember(target.getUniqueId())) {
                                sender.sendMessage(plugin.message(sender, "lead.command.join.player_already_in_team", VariableContext.of("player", target.getName())));
                                return;
                            }

                            TeamJoinByPlaceEvent joinEvent = new TeamJoinByPlaceEvent(team, target);
                            if (joinEvent.callEvent()) {
                                ITeam previousTeam = plugin.getTeam(target.getUniqueId());
                                if (previousTeam != null) previousTeam.removeMember(target.getUniqueId());

                                team.addMember(new TeamMember(target.getUniqueId(), target.getName()));
                                for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                    Player p = plugin.getServer().getPlayer(playerUniqueId);
                                    if (p != null)
                                        p.sendMessage(plugin.message(p, "lead.command.player_joined", VariableContext.of("player", target.getName())));
                                }

                                sender.sendMessage(plugin.message(sender, "lead.command.add.success", VariableContext.of("player", target.getName()).with("color", team.getColor()).with("name", team.getId())));
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
                .withPermission(convertPermission(plugin.config().getString("commands.remove", null)))
                .withArguments(new PlayerProfileArgument("target"))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof List<?> l) {
                        OfflinePlayer target = CommandHelper.convertPlayerProfileToOfflinePlayer((List<PlayerProfile>) l);
                        if (target == null || target.getName() == null) {
                            sender.sendMessage(plugin.message(sender, "lead.command.invite.player_not_online"));
                            return;
                        }

                        ITeam team = plugin.getTeam(target.getUniqueId());
                        if (team == null) {
                            sender.sendMessage(plugin.message(sender, "lead.command.join.team_not_found"));
                            return;
                        }

                        TeamRemoveEvent removeEvent = new TeamRemoveEvent(team, target.getUniqueId());
                        if (removeEvent.callEvent()) {
                            for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                                Player p = plugin.getServer().getPlayer(playerUniqueId);
                                if (p != null)
                                    p.sendMessage(plugin.message(p, "lead.command.player_removed", VariableContext.of("player", target.getName())));
                            }

                            new TeamLeaveEvent(target, team).callEvent();
                            team.removeMember(target.getUniqueId());

                            sender.sendMessage(plugin.message(sender, "lead.command.remove.success", VariableContext.of("player", target.getName()).with("color", team.getColor()).with("name", team.getId())));
                            if (plugin.config().getBoolean("automatic_updates", true))
                                plugin.update();

                            if (sender instanceof Player player)
                                CommandAPI.updateRequirements(player);
                        }
                    }
                })
        );
        withSubcommand(new CommandAPICommand("delete")
                .withPermission(convertPermission(plugin.config().getString("commands.delete", null)))
                .withArguments(new StringArgument("team_id").replaceSuggestions(ArgumentSuggestions.strings(s -> plugin.getTeams().stream().map(ITeam::getId).toArray(String[]::new))))
                .executes((sender, args) -> {
                    if (args.get(0) instanceof String targetTeam) {
                        ITeam team = plugin.getTeam(targetTeam);
                        if (team == null) {
                            sender.sendMessage(plugin.message(sender, "lead.command.edit.team_not_found"));
                            return;
                        }

                        for (UUID playerUniqueId : team.getMembers().stream().map(ITeamMember::getUniqueId).toList()) {
                            Player p = plugin.getServer().getPlayer(playerUniqueId);
                            if (p != null)
                                p.sendMessage(plugin.message(p, "lead.command.team_deleted", VariableContext.of("player", sender.getName())));
                        }

                        plugin.deleteTeam(team);
                        if (plugin.config().getBoolean("automatic_updates", true))
                            plugin.update();

                        if (sender instanceof Player player)
                            CommandAPI.updateRequirements(player);

                        sender.sendMessage(plugin.message(sender, "lead.command.delete.success", VariableContext.of("color", team.getColor()).with("name", team.getId())));
                    }
                })
        );
        withSubcommand(new CommandAPICommand("list")
                .withPermission(convertPermission(plugin.config().getString("commands.list", null)))
                .executesPlayer((player, args) -> {
                    if (plugin.getTeams().size() == 0) {
                        player.sendMessage(plugin.message(player, "lead.command.list.no_teams"));
                        return;
                    }

                    new TeamListMenu(plugin, player, 0).open();
                }));
        withSubcommand(new CommandAPICommand("help")
                .withPermission(convertPermission(plugin.config().getString("commands.help", null)))
                .executes(this::executeHelpCommand));
        executes(this::executeHelpCommand);
    }

    private CommandPermission convertPermission(String permission) {
        return permission == null ? CommandPermission.NONE : CommandPermission.fromString(permission);
    }

    private void executeHelpCommand(CommandSender sender, CommandArguments args) {
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.space")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.header")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.team_commands.create")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.team_commands.join")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.team_commands.list")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.space")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.member_commands")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.member_commands.leave")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.member_commands.invite")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.member_commands.chat")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.space")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.leader_commands")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.leader_commands.kick")));
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.leader_commands.disband")));
        if (sender.hasPermission("lodestone.lead.manage")) {
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.space")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.merge")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.place")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.remove")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.delete")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.modify_color")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.modify_display_name")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.modify_collidable")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.modify_name_tag")));
            sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.admin_commands.modify_friendly_fire")));
        }
        sender.sendMessage(MiniMessageHelper.deserialize(plugin.text(sender, "lead.command.list.space")));
    }
}
