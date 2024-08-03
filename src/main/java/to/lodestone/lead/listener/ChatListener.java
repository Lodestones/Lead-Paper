package to.lodestone.lead.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import to.lodestone.bookshelfapi.api.event.PlayerChatEvent;
import to.lodestone.bookshelfapi.api.util.MiniMessageUtil;
import to.lodestone.lead.LeadPaper;
import to.lodestone.leadapi.api.ITeam;
import to.lodestone.leadapi.api.ITeamMember;

public class ChatListener implements Listener {

    private final LeadPaper plugin;

    public ChatListener(LeadPaper plugin) {
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

            if (teamMember.isInTeamChat()) {
                event.setViewers(team.getMembers().stream().map(ITeamMember::getUniqueId).toList());
                event.prefix(MiniMessageUtil.deserialize("<green><bold>TEAM »<reset><%s> [%s]", team.getColor(), team.getName()));
                event.playerColor(team.getColor());
            } else {
                Component newPrefix = MiniMessageUtil.deserialize("<%s>[%s]", team.getColor(), team.getName());
                event.playerColor(team.getColor());
                if (event.prefix() == null)
                    event.prefix(newPrefix);
                else
                    event.prefix(event.prefix().append(Component.text(MiniMessageUtil.serialize(event.prefix()).isEmpty() ? "" : " ").append(newPrefix)));
            }

            event.setModified(true);
            return;
        }

        event.setModified(false);
    }

}
