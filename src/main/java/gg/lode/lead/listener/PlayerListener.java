package gg.lode.lead.listener;

import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.lead.LeadPlugin;
import gg.lode.leadapi.api.ITeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;

public class PlayerListener implements Listener {

    private final LeadPlugin plugin;

    public PlayerListener(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Task.runAsync(plugin, plugin::update);

        if (plugin.config().getBoolean("auto_assign")) {
            Player player = event.getPlayer();
            List<ITeam> teams = plugin.getTeams();

            if (teams.isEmpty()) return;

            ITeam targetTeam = plugin.getTeam(player.getUniqueId());
            if (targetTeam != null) return;

            ITeam team = teams.get(LeadPlugin.SEED.nextInt(teams.size()));
            team.addMember(player);

            // Teleport the player to the team's spawn point
            if (team.getSpawnLocation() != null) {
                player.teleport(team.getSpawnLocation());
            }

            player.sendMessage(MiniMessageUtil.deserialize(String.format(" \n  <green><bold>PLAYER JOINED\n  <reset><yellow>%s</yellow> <gray>has joined your team!\n ", player.getName())));
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getPlayerTeam(event.getPlayer());
        if (team == null) return;

        team.removePlayer(event.getPlayer());
        Task.laterAsync(plugin, plugin::update, 1);
    }

}
