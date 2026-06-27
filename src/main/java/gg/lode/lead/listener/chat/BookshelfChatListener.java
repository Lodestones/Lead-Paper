package gg.lode.lead.listener.chat;

import gg.lode.bookshelf.event.PlayerChatEvent;
import gg.lode.bookshelfapi.api.util.EnumHelper;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.bookshelfapi.api.util.VariableContext;
import gg.lode.lead.LeadPlugin;
import gg.lode.lead.team.TeamAlignment;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import gg.lode.leadapi.api.event.TeamMessageEvent;
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
                ctx.set("message", MiniMessageHelper.serialize(event.message()));
                ctx.set("messageColor", "#FFFFFF");
                ctx.set("colorNames", colorNames ? String.format("<%s>", team.getColor()) : "<reset>");

                TeamMessageEvent teamMessageEvent = new TeamMessageEvent(player, MiniMessageHelper.serialize(event.message()));
                if (teamMessageEvent.callEvent()) {
                    Component prefix = MiniMessageHelper.deserialize("<green><bold>TEAM » ");
                    Component teamLabel = ctx.replaceAsComponent("<reset><font:<font>><<teamColor>><teamName></font><colorNames> ");
                    Component messageColor = ctx.replaceAsComponent("<playerName>: <message>");

                    for (ITeamMember member : team.getMembers()) {
                        Player memberPlayer = plugin.getServer().getPlayer(member.getUniqueId());
                        if (memberPlayer != null) {
                            Component messageToSend = MiniMessageHelper.persistStyle(prefix, teamLabel).append(messageColor);
                            memberPlayer.sendMessage(messageToSend);
                        }
                    }
                }

                event.setCancelled(true);
                return;
            } else {
                Component newPrefix = MiniMessageHelper.persistStyle(MiniMessageHelper.deserialize(String.format("<font:%s><%s>%s</font>%s", plugin.config().getString("font", "default"), team.getColor(), team.getName(), String.format("<%s>", plugin.config().getBoolean("color_names") ? team.getColor() : "reset")))).decoration(TextDecoration.BOLD, false);

                // Use the leading/trailing hooks so the team tag sits BEFORE the rank
                // prefix (or after the suffix), instead of event.prefix()+setModified(false)
                // which lets the LuckPerms block prepend the rank and push the tag after it.
                switch (EnumHelper.fetchEnum(TeamAlignment.class, plugin.config().getString("team_alignment"), TeamAlignment.PREFIX)) {
                    case PREFIX -> event.addLeadingPrefix(newPrefix.append(Component.text(" ")));
                    case SUFFIX -> event.addTrailingSuffix(Component.text(" ").append(newPrefix));
                }
            }

            // The white player/message colors above flag the event as modified; reset that
            // so Bookshelf still applies the LuckPerms rank prefix/colors. The team tag is
            // positioned independently via the hooks above and is unaffected by this flag.
            event.setModified(false);
            return;
        }

    }

}
