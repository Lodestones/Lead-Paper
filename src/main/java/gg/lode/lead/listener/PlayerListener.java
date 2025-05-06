package gg.lode.lead.listener;

import gg.lode.bookshelfapi.api.Task;
import gg.lode.lead.LeadPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class PlayerListener implements Listener {

    private final LeadPlugin plugin;

    public PlayerListener(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Task.runAsync(plugin, plugin::update);
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
