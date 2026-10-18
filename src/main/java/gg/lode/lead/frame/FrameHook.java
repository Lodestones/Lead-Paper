package gg.lode.lead.frame;

import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.frameapi.FrameAPI;
import gg.lode.frameapi.api.FramePriority;
import gg.lode.frameapi.api.layer.FrameLayer;
import gg.lode.frameapi.api.nametag.CollisionRule;
import gg.lode.frameapi.api.nametag.NameTagStyle;
import gg.lode.frameapi.api.nametag.NameVisibility;
import gg.lode.frameapi.api.tablist.TabEntry;
import gg.lode.lead.LeadPlugin;
import gg.lode.lead.team.TeamAlignment;
import gg.lode.leadapi.api.ITeam;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Renders Lead teams through Frame instead of the vanilla scoreboard or TAB.
 * <p>
 * Frame resolves the tablist and nametag per (viewer, target). A Lead team
 * label depends only on the target, so this hook pushes one value per player
 * into a {@link FrameLayer} at the {@link FramePriority#LOW} band — broad,
 * always-on decoration that any feature plugin can override. Layer setters are
 * thread-safe and schedule their own refresh, so there is no separate apply
 * step.
 */
public class FrameHook {

    private static final String LAYER_ID = "teams";

    private final FrameLayer layer;

    public FrameHook(LeadPlugin plugin) {
        this.layer = FrameAPI.get().createLayer(plugin, LAYER_ID, FramePriority.LOW);
    }

    /**
     * Pushes the current team label for every online player, clearing the
     * entry for anyone no longer in a team.
     *
     * @param playerTeamMap resolved player-to-team map for this refresh.
     * @param alignment     whether the label renders as a prefix or suffix.
     * @param colorNames    whether the player's own name is tinted the team colour.
     * @param font          the MiniMessage font applied to the label.
     * @param onlinePlayers the players to render.
     */
    public void render(Map<UUID, ITeam> playerTeamMap, TeamAlignment alignment, boolean colorNames,
                       String font, Iterable<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            ITeam team = playerTeamMap.get(player.getUniqueId());
            if (team == null) {
                layer.setTabEntry(player, null);
                layer.setNameTag(player, null);
                continue;
            }
            layer.setTabEntry(player, buildTabEntry(player, team, alignment, colorNames, font));
            layer.setNameTag(player, buildNameTag(team, alignment, font));
        }
    }

    private TabEntry buildTabEntry(Player player, ITeam team, TeamAlignment alignment, boolean colorNames, String font) {
        String label = label(team, font);
        String nameColor = colorNames ? team.getColor() : "white";
        String name = player.getName();

        String display = label == null
                ? String.format("<%s>%s", nameColor, name)
                : alignment == TeamAlignment.SUFFIX
                ? String.format("<%s>%s %s", nameColor, name, label)
                : String.format("%s <%s>%s", label, nameColor, name);

        return TabEntry.builder()
                .displayName(MiniMessageHelper.deserialize(display))
                .sortKey(team.getId() + "_" + name)
                .build();
    }

    private NameTagStyle buildNameTag(ITeam team, TeamAlignment alignment, String font) {
        NameTagStyle.Builder builder = NameTagStyle.builder()
                .collision(mapCollision(team.getCollidable()))
                .visibility(mapVisibility(team.getNameTagVisibility()));

        String label = label(team, font);
        if (label != null) {
            if (alignment == TeamAlignment.SUFFIX) {
                builder.suffix(MiniMessageHelper.deserialize(" " + label));
            } else {
                builder.prefix(MiniMessageHelper.deserialize(label + " "));
            }
        }
        return builder.build();
    }

    /**
     * The coloured, fonted team label, or {@code null} when the team has no
     * displayable name.
     */
    private String label(ITeam team, String font) {
        String name = Objects.requireNonNullElse(team.getName(), team.getId());
        if (name == null || name.isEmpty()) return null;
        return String.format("<font:%s><%s>%s</font>", font, team.getColor(), name);
    }

    private CollisionRule mapCollision(Team.OptionStatus status) {
        return switch (status) {
            case ALWAYS -> CollisionRule.ALWAYS;
            case NEVER -> CollisionRule.NEVER;
            case FOR_OWN_TEAM -> CollisionRule.PUSH_OWN_TEAM;
            case FOR_OTHER_TEAMS -> CollisionRule.PUSH_OTHER_TEAMS;
        };
    }

    private NameVisibility mapVisibility(Team.OptionStatus status) {
        return switch (status) {
            case ALWAYS -> NameVisibility.ALWAYS;
            case NEVER -> NameVisibility.NEVER;
            // Bukkit FOR_OWN_TEAM = shown to own team only = hidden for other teams.
            case FOR_OWN_TEAM -> NameVisibility.HIDE_FOR_OTHER_TEAMS;
            case FOR_OTHER_TEAMS -> NameVisibility.HIDE_FOR_OWN_TEAM;
        };
    }

    /** Drops every value this hook pushed. */
    public void clear() {
        layer.clearAll();
    }
}
