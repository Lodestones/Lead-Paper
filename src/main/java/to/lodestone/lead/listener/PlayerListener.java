package to.lodestone.lead.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import to.lodestone.bookshelfapi.api.Task;
import to.lodestone.bookshelfapi.api.util.MiniMessageUtil;
import to.lodestone.lead.LeadPlugin;

public class PlayerListener implements Listener {

    private final LeadPlugin plugin;

    public PlayerListener(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (plugin.isKofiDonor()) {
                if (player.isOp()) {
                    player.sendMessage(Component.empty());
                    player.sendMessage(MiniMessageUtil.deserialize("  <yellow><bold>Lead - The Ultimate Teams Plugin"));
                    player.sendMessage(MiniMessageUtil.deserialize(String.format("  <white>Running <yellow>%s", LeadPlugin.VERSION)));
                    player.sendMessage(MiniMessageUtil.deserialize("  <white>Join the Lodestone <hover:show_text:'<#5C77FB>Join the Discord'><click:open_url:https://discord.gg/lodestone><underlined><#5C77FB>discord!"));
                    player.sendMessage(Component.empty());
                }
            } else {
                player.sendMessage(Component.empty());
                player.sendMessage(MiniMessageUtil.deserialize("  <yellow><bold>Lead - The Ultimate Teams Plugin"));
                player.sendMessage(MiniMessageUtil.deserialize(String.format("  <white>Running <yellow>%s", LeadPlugin.VERSION)));
                player.sendMessage(MiniMessageUtil.deserialize("  <white>Download Lead at <hover:show_text:'<green>Download Lead at Modrinth!'><click:open_url:https://modrinth.com/plugin/lead><underlined><green>Modrinth!"));
                player.sendMessage(MiniMessageUtil.deserialize("  <white>Consider donating to my <hover:show_text:'<#E338D4>Donate to my ko-fi!'><click:open_url:https://ko-fi.com/apollo30><underlined><#E338D4>ko-fi!"));
                player.sendMessage(MiniMessageUtil.deserialize("  <white>Join the Lodestone <hover:show_text:'<#5C77FB>Join the Discord'><click:open_url:https://discord.gg/lodestone><underlined><#5C77FB>discord!"));
                player.sendMessage(Component.empty());
            }
        }, 10L);

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
