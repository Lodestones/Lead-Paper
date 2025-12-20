package gg.lode.lead.listener.chat;

import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.bookshelfapi.api.util.VariableContext;
import gg.lode.lead.LeadPlugin;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class SpigotChatListener implements Listener {

    private final LeadPlugin plugin;

    public SpigotChatListener(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void on(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ITeam team = plugin.getTeam(player.getUniqueId());

        VariableContext ctx = new VariableContext();
        event.setCancelled(true); // Cancel default chat behavior

        // Basic player values
        String playerName = player.getName();
        String font = plugin.config().getString("font", "default");

        if (team != null) {
            ITeamMember teamMember = team.getMember(player.getUniqueId());
            assert teamMember != null;

            // Common team values
            String teamName = team.getName();
            String teamColor = team.getColor(); // HEX or named color string from config
            boolean colorNames = plugin.config().getBoolean("color_names");

            ctx.set("font", font);
            ctx.set("teamName", teamName);
            ctx.set("teamColor", teamColor);
            ctx.set("playerName", playerName);
            ctx.set("message", MiniMessageHelper.serialize(event.message()));
            ctx.set("messageColor", teamMember.isInTeamChat() ? "#FFFFFF" : teamColor);
            ctx.set("colorNames", colorNames ? String.format("<%s>", teamColor) : "<reset>");

            if (teamMember.isInTeamChat()) {
                // Team Chat prefix and message color
                Component prefix = MiniMessageHelper.deserialize("<green><bold>TEAM » ");
                Component teamLabel = ctx.replaceAsComponent("<reset><font:<font>><<teamColor>><teamName></font><<teamColor>> ");
                Component messageColor = ctx.replaceAsComponent("<playerName>: <message>");

                for (ITeamMember member : team.getMembers()) {
                    Player memberPlayer = plugin.getServer().getPlayer(member.getUniqueId());
                    if (memberPlayer != null) {
                        Component messageToSend = MiniMessageHelper.persistStyle(prefix, teamLabel).append(messageColor);
                        memberPlayer.sendMessage(messageToSend);
                    }
                }
            } else {
                // Public team message with optional name coloring
                Component teamPrefix = ctx.replaceAsComponent("<font:<font>><<teamColor>><teamName></font><colorNames> ").decoration(TextDecoration.BOLD, false);
                Component messageColor = ctx.replaceAsComponent("<playerName>: <message>");

                Component messageToSend = teamPrefix
                        .append(Component.empty().decoration(TextDecoration.BOLD, false))
                        .append(messageColor);

                Bukkit.broadcast(messageToSend);
            }

            return;
        }

        // Default global message for non-teamed players
        Component messageToSend = MiniMessageHelper.deserialize(String.format("<gray>%s: %s", playerName, MiniMessageHelper.serialize(event.message())));
        Bukkit.broadcast(messageToSend);
    }

}
