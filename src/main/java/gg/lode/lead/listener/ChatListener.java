package gg.lode.lead.listener;

import gg.lode.bookshelfapi.api.event.PlayerChatEvent;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.lead.LeadPlugin;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final LeadPlugin plugin;

    public ChatListener(LeadPlugin plugin) {
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
                event.setViewers(team.getMembers().stream().map(ITeamMember::getUniqueId).toList());
                event.prefix(MiniMessageUtil.persistStyle(MiniMessageUtil.deserialize("<green><bold>TEAM » "), MiniMessageUtil.deserialize("<reset><font:%s><%s>%s</font><%s>", plugin.config().getString("font", "default"), team.getColor(), team.getName(), team.getColor())));
                event.playerColor(team.getColor());
            } else {
                Component newPrefix = MiniMessageUtil.persistStyle(MiniMessageUtil.deserialize("<font:%s><%s>%s</font>%s", plugin.config().getString("font", "default"), team.getColor(), team.getName(), String.format("<%s>", plugin.config().getBoolean("color_names") ? team.getColor() : "reset"))).decoration(TextDecoration.BOLD, false);
                if (event.prefix() == null)
                    event.prefix(newPrefix);
                else
                    event.prefix(event.prefix().append(Component.empty().decoration(TextDecoration.BOLD, false)).append(Component.text(MiniMessageUtil.serialize(event.prefix()).isEmpty() ? "" : " ").append(newPrefix)));
            }

            return;
        }

        event.setModified(false);
    }

}
