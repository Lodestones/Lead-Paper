package gg.lode.lead.scoreboard;

import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.lead.LeadPlugin;
import gg.lode.lead.team.TeamAlignment;
import gg.lode.leadapi.api.ITeam;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mirrors Lead teams onto the main vanilla scoreboard.
 * <p>
 * Two modes:
 * <ul>
 *     <li>{@link Mode#DISPLAY} — Lead owns the visuals too, so prefixes,
 *     suffixes, options and the player's active scoreboard are all written.
 *     Used when neither TAB nor Frame is installed.</li>
 *     <li>{@link Mode#SELECTOR} — TAB or Frame owns the visuals. Only team
 *     membership (plus the server-side gameplay options) is written, so that
 *     vanilla target selectors such as {@code @a[team=1]} keep resolving.
 *     Prefixes and suffixes are cleared so nothing is rendered twice, and the
 *     player's active scoreboard is left alone.</li>
 * </ul>
 * The scoreboard team name is whatever {@link SelectorKey} asks for, sanitised
 * so it can never produce a team the client refuses — an unsanitised name is
 * how a long or formatted display name turns into a disconnect. Names that
 * would collide with another Lead team, or with a team some other plugin owns,
 * fall back to the id rather than hijacking it.
 * <p>
 * Every method must run on the main thread.
 */
public class ScoreboardSync {

    public enum Mode {
        DISPLAY,
        SELECTOR
    }

    private static final String TEAMLESS_ID = "TEAMLESS";

    /**
     * Vanilla's own team-name limit. Longer names are accepted by the API on
     * modern Paper but are not safe across every client, so everything is cut
     * to the length the game itself guarantees.
     */
    private static final int MAX_NAME_LENGTH = 16;

    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("</?[^<>\\s][^<>]*>");
    private static final Pattern LEGACY_COLOR = Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");
    private static final Pattern ILLEGAL = Pattern.compile("[\"'\\\\\\p{Cntrl}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final LeadPlugin plugin;

    /**
     * Lead team id to the scoreboard team this sync registered for it. Lets
     * SELECTOR mode leave every other plugin's team alone, and lets a renamed
     * team drop the board team it used to own.
     */
    private final Map<String, String> managedTeams = new HashMap<>();

    /** Lead team ids already warned about, so a permanent collision logs once. */
    private final Set<String> warnedTeams = new HashSet<>();

    public ScoreboardSync(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    public void sync(Mode mode,
                     List<ITeam> leadTeams,
                     Map<UUID, ITeam> playerTeamMap,
                     Map<String, Set<String>> teamMemberNames,
                     TeamAlignment alignment,
                     SelectorKey selectorKey,
                     boolean verbose) {
        boolean display = mode == Mode.DISPLAY;
        List<Player> onlinePlayers = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Work out the board name for every team first: a name can only be
        // claimed once, and the cleanup below needs to know the final set.
        Map<String, String> desired = new LinkedHashMap<>();
        Set<String> claimed = new HashSet<>();
        for (ITeam team : leadTeams) {
            String boardName = resolveBoardName(team, selectorKey, scoreboard, claimed);
            if (boardName == null) continue;
            desired.put(team.getId(), boardName);
            claimed.add(boardName);
        }

        Team remainingTeam = null;
        if (display) {
            // Create or get a team for players without a specific team
            remainingTeam = getOrRegister(scoreboard, TEAMLESS_ID, verbose);
            if (remainingTeam != null) {
                remainingTeam.prefix(Component.empty());
                remainingTeam.suffix(Component.empty());
                remainingTeam.setAllowFriendlyFire(true);

                // Remove any players who are in the remaining team but are in a team.
                for (Player player : onlinePlayers) {
                    if (playerTeamMap.containsKey(player.getUniqueId()) && remainingTeam.hasEntry(player.getName())) {
                        debug(verbose, String.format("Removing %s from the remaining team.", player.getName()));
                        remainingTeam.removeEntry(player.getName());
                    }
                }
            }
        }

        // Remove any teams that are no longer a part of the lead team list,
        // including board teams a rename just orphaned.
        for (Team bukkitTeam : new ArrayList<>(scoreboard.getTeams())) {
            String name = bukkitTeam.getName();
            if (name.equals(TEAMLESS_ID) || claimed.contains(name)) continue;
            // In SELECTOR mode TAB/Frame and other plugins own their own teams — only drop ours.
            if (!display && !managedTeams.containsValue(name)) continue;
            if (scoreboard.getTeam(name) == null) continue;

            debug(verbose, String.format("Removing scoreboard team named %s.", name));
            bukkitTeam.unregister();
            managedTeams.values().remove(name);
        }

        for (ITeam team : leadTeams) {
            String boardName = desired.get(team.getId());
            if (boardName == null) continue;

            try {
                Team bukkitTeam = getOrRegister(scoreboard, boardName, verbose);
                if (bukkitTeam == null) continue;
                managedTeams.put(team.getId(), boardName);

                if (display) {
                    switch (alignment) {
                        case PREFIX -> {
                            bukkitTeam.prefix(MiniMessageHelper.deserialize(String.format("<%s>%s ", team.getColor(), Objects.requireNonNullElse(team.getName(), team.getId()))));
                            bukkitTeam.suffix(Component.empty());
                        }
                        case SUFFIX -> {
                            bukkitTeam.suffix(MiniMessageHelper.deserialize(String.format(" <%s>%s", team.getColor(), Objects.requireNonNullElse(team.getName(), team.getId()))));
                            bukkitTeam.prefix(Component.empty());
                        }
                    }
                } else {
                    // TAB/Frame render the label; a vanilla prefix here would double it up.
                    bukkitTeam.prefix(Component.empty());
                    bukkitTeam.suffix(Component.empty());
                }

                bukkitTeam.setOption(Team.Option.COLLISION_RULE, team.getCollidable());
                bukkitTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, team.getNameTagVisibility());
                bukkitTeam.setAllowFriendlyFire(team.isFriendlyFireAllowed());

                Set<String> leadMemberNames = teamMemberNames.get(team.getId());
                if (leadMemberNames == null) leadMemberNames = new HashSet<>();

                // Remove any team members that are no longer a part of the lead team member list.
                for (String bukkitMember : new HashSet<>(bukkitTeam.getEntries())) {
                    Objects.requireNonNull(bukkitMember);
                    if (!leadMemberNames.contains(bukkitMember)) {
                        Player player = Bukkit.getPlayerExact(bukkitMember);
                        if (player != null && player.isOnline()) {
                            debug(verbose, String.format("Removing %s from scoreboard team %s.", bukkitMember, boardName));
                            bukkitTeam.removeEntry(bukkitMember);
                        }
                    }
                }

                // Create any team members that haven't been created yet.
                for (String leadMemberName : leadMemberNames) {
                    if (!bukkitTeam.hasEntry(leadMemberName)) {
                        debug(verbose, String.format("Adding %s to scoreboard team %s.", leadMemberName, boardName));
                        bukkitTeam.addEntry(leadMemberName);
                    }
                }
            } catch (Exception e) {
                // One broken team must not stop the rest of the sync.
                plugin.getLogger().warning(String.format("Failed to sync team \"%s\" to the scoreboard: %s", team.getId(), e.getMessage()));
            }
        }

        if (!display || remainingTeam == null) return;

        // Add any players who aren't in a team in the remaining team list.
        for (Player player : onlinePlayers) {
            if (!playerTeamMap.containsKey(player.getUniqueId()) && !remainingTeam.hasEntry(player.getName())) {
                debug(verbose, String.format("Adding %s to the remaining team.", player.getName()));
                remainingTeam.addEntry(player.getName());
            }
        }

        // Only set scoreboard for players whose scoreboard actually changed
        for (Player player : onlinePlayers) {
            if (player.isOnline() && player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }
        }
    }

    /**
     * The scoreboard team name this Lead team should own, or {@code null} when
     * every candidate is already taken by something Lead does not control.
     */
    private String resolveBoardName(ITeam team, SelectorKey selectorKey, Scoreboard scoreboard, Set<String> claimed) {
        String id = sanitize(team.getId());
        if (id == null) {
            warnOnce(team.getId(), String.format("Team \"%s\" has no usable id, so it is not mirrored onto the scoreboard.", team.getId()));
            return null;
        }

        String preferred = selectorKey == SelectorKey.NAME ? sanitize(team.getName()) : null;
        // A display name that collides gets the id appended rather than
        // silently pointing a selector at the wrong team.
        String[] candidates = preferred == null
                ? new String[]{id}
                : new String[]{preferred, truncate(preferred + " " + id), id};

        for (String candidate : candidates) {
            if (candidate == null || claimed.contains(candidate)) continue;
            if (!isClaimable(scoreboard, candidate)) continue;
            warnedTeams.remove(team.getId());
            return candidate;
        }

        warnOnce(team.getId(), String.format("Team \"%s\" could not claim a scoreboard team name — another plugin already owns every candidate.", team.getId()));
        return null;
    }

    /** A name is claimable when no team holds it, or the team holding it is one of ours. */
    private boolean isClaimable(Scoreboard scoreboard, String name) {
        return scoreboard.getTeam(name) == null || managedTeams.containsValue(name);
    }

    private Team getOrRegister(Scoreboard scoreboard, String name, boolean verbose) {
        Team existing = scoreboard.getTeam(name);
        if (existing != null) return existing;

        debug(verbose, String.format("Creating scoreboard team named %s.", name));
        try {
            return scoreboard.registerNewTeam(name);
        } catch (IllegalArgumentException e) {
            // Lost a race, or the server rejected the name outright.
            Team raced = scoreboard.getTeam(name);
            if (raced != null) return raced;
            plugin.getLogger().warning(String.format("Could not register scoreboard team \"%s\": %s", name, e.getMessage()));
            return null;
        }
    }

    /**
     * Strips formatting and anything the client or the selector parser cannot
     * take, then cuts the result to vanilla's team-name limit.
     */
    private static String sanitize(String raw) {
        if (raw == null) return null;
        String cleaned = MINI_MESSAGE_TAG.matcher(raw).replaceAll("");
        cleaned = LEGACY_COLOR.matcher(cleaned).replaceAll("");
        cleaned = ILLEGAL.matcher(cleaned).replaceAll("");
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
        return truncate(cleaned);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        String cut = value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH).trim() : value;
        return cut.isEmpty() ? null : cut;
    }

    /** Drops every scoreboard team this sync registered. */
    public void clear() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (String name : new HashSet<>(managedTeams.values())) {
            Team bukkitTeam = scoreboard.getTeam(name);
            if (bukkitTeam != null) bukkitTeam.unregister();
        }
        managedTeams.clear();
    }

    private void warnOnce(String teamId, String message) {
        if (teamId != null && !warnedTeams.add(teamId)) return;
        plugin.getLogger().warning(message);
    }

    private void debug(boolean verbose, String message) {
        if (!verbose) return;
        Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: %s]", message)), "lead.debug");
    }
}
