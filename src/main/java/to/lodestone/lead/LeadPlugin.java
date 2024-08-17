package to.lodestone.lead;

import dev.jorel.commandapi.CommandAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;
import to.lodestone.bookshelfapi.BookshelfAPI;
import to.lodestone.bookshelfapi.IBookshelfAPI;
import to.lodestone.bookshelfapi.api.Configuration;
import to.lodestone.bookshelfapi.api.KofiManager;
import to.lodestone.bookshelfapi.api.VersionUpdater;
import to.lodestone.bookshelfapi.api.util.EnumUtil;
import to.lodestone.bookshelfapi.api.util.Metrics;
import to.lodestone.bookshelfapi.api.util.MiniMessageUtil;
import to.lodestone.lead.command.TeamCommand;
import to.lodestone.lead.command.TeamMessageCommand;
import to.lodestone.lead.listener.ChatListener;
import to.lodestone.lead.listener.PlayerListener;
import to.lodestone.lead.listener.WorldListener;
import to.lodestone.lead.team.Team;
import to.lodestone.lead.team.TeamMember;
import to.lodestone.leadapi.ILeadAPI;
import to.lodestone.leadapi.LeadAPI;
import to.lodestone.leadapi.api.ITeam;
import to.lodestone.leadapi.api.ITeamMember;
import to.lodestone.leadapi.api.exception.TeamAlreadyExistsException;
import to.lodestone.leadapi.api.exception.TeamNotFoundException;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

public final class LeadPlugin extends JavaPlugin implements ILeadAPI {

    public static final String VERSION = "v1.1.3";
    private static final int CONFIG_VERSION = 2;
    private static final String TEAMLESS_ID = "TEAMLESS";

    private List<ITeam> teams;
    public static Random SEED = new Random();
    private Configuration config;
    private Configuration team;
    private KofiManager kofiManager;

    @Override
    public void onLoad() {
        this.team = new Configuration(this, "teams.yml");
        this.team.initialize();

        this.config = new Configuration(this, "config.yml");
        this.config.initialize();
    }

    @Override
    public void onEnable() {
        new Metrics(this, 22603); // bStats

        LeadAPI.setApi(this);
        teams = new ArrayList<>();

        if (getServer().getPluginManager().isPluginEnabled("TAB")) {
            getLogger().severe("=============================================");
            getLogger().severe("TAB Plugin DETECTED!");
            getLogger().severe("WARNING! Lead will NOT display teams if you have another plugin that modifies scoreboards.");
            getLogger().severe("=============================================");
        }

        if (getServer().getPluginManager().isPluginEnabled("NoChatReports")) {
            getLogger().severe("=============================================");
            getLogger().severe("NoChatReports Plugin DETECTED!");
            getLogger().severe("WARNING! Lead will MAY not display chat if you have another plugin that modifies chat.");
            getLogger().severe("Lead is already a replacement for NoChatReports, you can uninstall it.");
            getLogger().severe("=============================================");
        }

        this.registerCommands();

        // Load in teams from database
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "teams.yml"));
        for (String teamUniqueId : configuration.getKeys(false)) {
            ConfigurationSection section = configuration.getConfigurationSection(teamUniqueId);
            if (section == null) continue;
            ArrayList<ITeamMember> members = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList("members")) {
                for (String uniqueId : map.keySet().stream().map(String.class::cast).toList()) {
                    members.add(new TeamMember(UUID.fromString(uniqueId), ((String) map.get(uniqueId))));
                }
            }

            Team team = new Team(
                    section.getString("id", section.getString("name")),
                    section.getString("name"),
                    section.getString("color", "#FFFFFF"),
                    UUID.fromString(teamUniqueId),
                    UUID.fromString(Objects.requireNonNull(section.getString("leaderUniqueId"))),
                    members,
                    new ArrayList<>(section.getStringList("invitations").stream().map(UUID::fromString).toList()),
                    EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, section.getString("collidable"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS),
                    EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, section.getString("name_tag_visibility"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS),
                    section.getBoolean("is_friendly_fire_allowed", false)
            );
            teams.add(team);
        }

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new VersionUpdater(this, "Lead", "https://modrinth.com/plugin/lead", "https://api.modrinth.com/v2/project/lead/version", VERSION), this);

        this.kofiManager = new KofiManager(config.getString("donation_key", null));
    }

    public boolean isKofiDonor() {
        return this.kofiManager.isKofiDonor();
    }

    public IBookshelfAPI bookshelf() {
        return BookshelfAPI.getApi();
    }

    private void registerCommands() {
        CommandAPI.unregister("team", true);
        CommandAPI.unregister("teammsg", true);

        new TeamMessageCommand(this).register();
        new TeamCommand(this).register();
    }

    public Configuration config() {
        return config;
    }

    @Override
    public void onDisable() {
        this.save();
    }

    @Override
    public void save() {
        // Wipe all concurrent teams first.
        for (String key : team.get().getKeys(false))
            team.set(key, null);

        for (ITeam team : getTeams()) team.save(this.team.get());
        this.team.save();
    }

    @Nullable
    public ITeam getTeam(UUID member) {
        return teams.stream().filter(team -> team.containsMember(member)).findFirst().orElse(null);
    }

    @Nullable
    public ITeam getTeam(int number) {
        return teams.stream().filter(team -> team.getId().matches("[0-9]*") && team.getNameAsNumber() == number).findFirst().orElse(null);
    }

    public @Nullable ITeam getTeam(String id) {
        return teams.stream().filter(team -> team.getId().equals(id)).findFirst().orElse(null);
    }

    public int getAvailableTeamNumber() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            if (getTeam(i) == null) {
                numbers.add(i);
            }
        }

        if (numbers.size() == 0)
            throw new IllegalStateException("No available team number!");

        Collections.shuffle(numbers, SEED);
        return numbers.get(0);
    }

    /**
     * Updates the tab list, ensuring that all teams are up-to-date.
     *
     * @author Apollo
     */
    @Override
    public void update() {
        for (Player plr : getServer().getOnlinePlayers()) {
            Scoreboard scoreboard = plr.getScoreboard();

            // Create or get a team for players without a specific team
            org.bukkit.scoreboard.Team remainingTeam = scoreboard.getTeam(TEAMLESS_ID);
            if (remainingTeam == null) remainingTeam = scoreboard.registerNewTeam(TEAMLESS_ID);

            remainingTeam.prefix(Component.empty());
            remainingTeam.suffix(Component.empty());
            remainingTeam.setAllowFriendlyFire(true);

            // Remove any players who are in the remaining team but are in a team.
            for (Player player : getServer().getOnlinePlayers().stream().filter(player -> getTeam(player.getUniqueId()) != null).toList()) {
                if (remainingTeam.hasEntry(player.getName()))
                    remainingTeam.removeEntry(player.getName());
            }

            // Add any players who aren't in a team in the remaining team list.
            for (Player player : getServer().getOnlinePlayers().stream().filter(player -> getTeam(player.getUniqueId()) == null).toList()) {
                if (!remainingTeam.hasEntry(player.getName()))
                    remainingTeam.addEntry(player.getName());
            }

            // Remove any teams that are no longer a part of the lead team list.
            scoreboard.getTeams().stream().filter(bukkitTeam -> !bukkitTeam.getName().equals(TEAMLESS_ID) && getTeams().stream().noneMatch(leadTeam -> leadTeam.getId().equals(bukkitTeam.getName())))
                    .forEach(org.bukkit.scoreboard.Team::unregister);

            // Create any teams that haven't been created yet.
            getTeams().stream().filter(leadTeam -> scoreboard.getTeams().stream().noneMatch(bukkitTeam -> bukkitTeam.getName().equals(leadTeam.getId())))
                    .forEach(leadTeam -> scoreboard.registerNewTeam(leadTeam.getId()));

            // Loop through every team now that we know that these teams exist.
            getTeams().forEach(team -> {
                @NotNull org.bukkit.scoreboard.Team bukkitTeam = Objects.requireNonNull(scoreboard.getTeam(team.getId()));
                bukkitTeam.prefix(MiniMessageUtil.deserialize(String.format("<%s>%s ", team.getColor(), Objects.requireNonNullElse(team.getName(), team.getId()))));
                bukkitTeam.suffix(Component.empty());
                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, config.getString("collision_rule"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS));
                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, config.getString("name_tag_visibility"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS));
                bukkitTeam.setAllowFriendlyFire(team.isFriendlyFireAllowed());

                // Remove any team members that are no longer a part of the lead team member list.
                bukkitTeam.getEntries().forEach(bukkitMember -> {
                    if (team.getMembers().stream().noneMatch(leadMember -> leadMember.getName().equals(bukkitMember)))
                        bukkitTeam.removeEntry(bukkitMember);
                });

                // Create any team members that haven't been created yet.
                team.getMembers().stream().filter(leadMember -> !bukkitTeam.hasEntry(leadMember.getName()))
                        .forEach(leadMember -> bukkitTeam.addEntry(leadMember.getName()));
            });
        }

//        for (Player plr : getServer().getOnlinePlayers()) {
//            Scoreboard scoreboard = plr.getScoreboard();
//
//            // Create or get a team for players without a specific team
//            org.bukkit.scoreboard.Team remainingTeam = scoreboard.getTeam("Unranked");
//            if (remainingTeam == null) {
//                remainingTeam = scoreboard.registerNewTeam("Unranked");
//            }
//            remainingTeam.setAllowFriendlyFire(true);
//            remainingTeam.prefix(Component.empty());
//            remainingTeam.suffix(Component.empty());
//
//            List<String> teamlessPlayers = getServer().getOnlinePlayers().stream().filter(p -> getTeam(p.getUniqueId()) == null).map(Player::getName).toList();
//
//            for (String teamlessPlayer : teamlessPlayers) {
//                if (remainingTeam.hasEntry(teamlessPlayer)) return;
//                else remainingTeam.addEntry(teamlessPlayer);
//            }
//
//            // Remove player from entry if they were remaining and now have a team.
//            if (!teamlessPlayers.contains(plr.getName()) && remainingTeam.hasEntry(plr.getName()))
//                remainingTeam.removeEntry(plr.getName());
//
//            List<ITeam> sortedTeams = getTeams();
//            if (sortedTeams.stream().allMatch(t -> t.getId().matches("[0-9]*")))
//                sortedTeams.sort(Comparator.comparingInt(ITeam::getNameAsNumber));
//
//            for (ITeam team : sortedTeams) {
//                org.bukkit.scoreboard.Team bukkitTeam = scoreboard.getTeam(team.getId());
//
//                // If the team does not exist, create it
//                if (bukkitTeam == null) {
//                    bukkitTeam = scoreboard.registerNewTeam(team.getId());
//                }
//
//                bukkitTeam.prefix(MiniMessageUtil.deserialize(String.format("<%s>[%s] ", team.getColor(), team.getId())));
//                bukkitTeam.suffix(Component.empty());
//                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
//                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
//                bukkitTeam.setAllowFriendlyFire(config().getBoolean("allow_friendly_fire"));
//                bukkitTeam.setNameTagVisibility(EnumUtil.fetchEnum(NameTagVisibility.class, config.getString("name_tag_visibility"), NameTagVisibility.ALWAYS));
//
//                // Add the players to the respective bukkitTeam
//                for (ITeamMember teamMember : team.getMembers()) {
//                    Player player = getServer().getPlayer(teamMember.getUniqueId());
//                    if (player == null) continue; // player is offline;
//                    bukkitTeam.addEntry(player.getName());
//                }
//            }
//        }
    }

    @Override
    public ITeam createTeam(String name) throws TeamAlreadyExistsException {
        if (teams.stream().anyMatch(t -> t.getId().equalsIgnoreCase(name))) throw new TeamAlreadyExistsException();
        Team team = new Team(name, "#FFFFFF");
        teams.add(team);
        return team;
    }

    @Override
    public ITeam deleteTeam(String name) throws TeamNotFoundException {
        ITeam team = teams.stream().filter(t -> t.getId().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (team == null) throw new TeamNotFoundException();
        teams.removeIf(t -> t.getId().equals(team.getId()));
        return team;
    }

    @Override
    public List<ITeam> getTeams() {
        return teams;
    }
}
