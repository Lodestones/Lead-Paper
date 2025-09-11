package gg.lode.lead.listener.chat;

import gg.lode.bookshelf.event.PlayerChatEvent;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.bookshelfapi.api.util.VariableContext;
import gg.lode.lead.LeadPlugin;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class BookshelfChatListener implements Listener {

    private final LeadPlugin plugin;

    public BookshelfChatListener(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void on(PlayerChatEvent event) {
        Player player = event.getPlayer();
        ITeam team = plugin.getTeam(player.getUniqueId());

        if (team != null) {
            ITeamMember teamMember = team.getMember(player.getUniqueId());
            assert teamMember != null;

            event.messageColor("#FFFFFF");
            event.playerColor("#FFFFFF");

            if (teamMember.isInTeamChat()) {
                VariableContext ctx = new VariableContext();
                String font = plugin.config().getString("font", "default");
                boolean colorNames = plugin.config().getBoolean("color_names");

                ctx.set("font", font);
                ctx.set("teamName", team.getName());
                ctx.set("teamColor", team.getColor());
                ctx.set("playerName", player.getName());
                ctx.set("message", MiniMessageUtil.serialize(event.message()));
                ctx.set("messageColor", "#FFFFFF");
                ctx.set("colorNames", colorNames ? String.format("<%s>", team.getColor()) : "<reset>");

                Component prefix = MiniMessageUtil.deserialize("<green><bold>TEAM » ");
                Component teamLabel = ctx.replaceAsComponent("<reset><font:<font>><<teamColor>><teamName></font><colorNames> ");
                Component messageColor = ctx.replaceAsComponent("<playerName>: <message>");

                for (ITeamMember member : team.getMembers()) {
                    Player memberPlayer = plugin.getServer().getPlayer(member.getUniqueId());
                    if (memberPlayer != null) {
                        Component messageToSend = MiniMessageUtil.persistStyle(prefix, teamLabel).append(messageColor);
                        memberPlayer.sendMessage(messageToSend);
                    }
                }

                event.setCancelled(true);
                return;
            } else {
                Component newPrefix = MiniMessageUtil.persistStyle(MiniMessageUtil.deserialize("<font:%s><%s>%s</font>%s", plugin.config().getString("font", "default"), team.getColor(), team.getName(), String.format("<%s>", plugin.config().getBoolean("color_names") ? team.getColor() : "reset"))).decoration(TextDecoration.BOLD, false);
                if (event.prefix() == null)
                    event.prefix(newPrefix);
                else
                    event.prefix(event.prefix().append(Component.empty().decoration(TextDecoration.BOLD, false)).append(Component.text(MiniMessageUtil.serialize(event.prefix()).isEmpty() ? "" : " ").append(newPrefix)));
            }

            return;
        }

        event.setModified(true);
    }

}
