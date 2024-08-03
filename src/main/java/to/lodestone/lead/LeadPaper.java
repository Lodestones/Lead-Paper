package to.lodestone.lead;

import dev.jorel.commandapi.CommandAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import to.lodestone.bookshelfapi.BookshelfAPI;
import to.lodestone.bookshelfapi.IBookshelfAPI;
import to.lodestone.bookshelfapi.api.Configuration;
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
import java.io.IOException;
import java.util.*;

public final class LeadPaper extends JavaPlugin implements ILeadAPI {

    public static final String VERSION = "beta-v1.1.0";
    private static final int CONFIG_VERSION = 1;

    private List<ITeam> teams;
    public static Random SEED = new Random();
    private Configuration config;

    @Override
    public void onLoad() {
        this.config = new Configuration(this, "config.yml");
        this.config.initialize();
        new Configuration(this, "teams.yml").initialize();
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
                    section.getString("name"),
                    section.getString("color", "#FFFFFF"),
                    UUID.fromString(teamUniqueId),
                    UUID.fromString(Objects.requireNonNull(section.getString("leaderUniqueId"))),
                    members,
                    new ArrayList<>(section.getStringList("invitations").stream().map(UUID::fromString).toList())
            );
            teams.add(team);
        }

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new VersionUpdater(this, "Lead", "https://modrinth.com/plugin/lead", "https://api.modrinth.com/v2/project/lead/version", VERSION), this);
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
        File teamFile = new File(getDataFolder(), "teams.yml");
        FileConfiguration teamConfig = YamlConfiguration.loadConfiguration(teamFile);

        // Wipe all concurrent teams first.
        for (String key : teamConfig.getKeys(false))
            teamConfig.set(key, null);

        for (ITeam team : getTeams()) {
            try {
                team.save(teamConfig);
                teamConfig.save(new File(getDataFolder(), "teams.yml"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nullable
    public ITeam getTeam(UUID member) {
        return teams.stream().filter(team -> team.containsMember(member)).findFirst().orElse(null);
    }


    @Nullable
    public ITeam getTeam(int number) {
        return teams.stream().filter(team -> team.getName().matches("[0-9]*") && team.getNameAsNumber() == number).findFirst().orElse(null);
    }

    public @Nullable ITeam getTeam(String name) {
        return teams.stream().filter(team -> team.getName().equals(name)).findFirst().orElse(null);
    }

    public int getAvailableTeamNumber() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            numbers.add(i);
        }

        Collections.shuffle(numbers, SEED);

        for (int num : numbers) {
            if (getTeam(num) == null) {
                return num;
            }
        }

        throw new IllegalStateException("No available team number!");
    }

    /**
     * Updates the tab list, ensuring that all teams are up-to-date.
     * @author Apollo
     */
    @Override
    @SuppressWarnings("deprecation")
    public void update() {
        for (Player plr : getServer().getOnlinePlayers()) {
            Scoreboard scoreboard = plr.getScoreboard();

            // Create or get a team for players without a specific team
            org.bukkit.scoreboard.Team remainingTeam = scoreboard.getTeam("Unranked");
            if (remainingTeam == null) {
                remainingTeam = scoreboard.registerNewTeam("Unranked");
            }
            remainingTeam.setAllowFriendlyFire(true);
            remainingTeam.prefix(Component.empty());
            remainingTeam.suffix(Component.empty());

            // Clear entries for remainingTeam to add the correct players later
            for (String entry : new ArrayList<>(remainingTeam.getEntries())) {
                remainingTeam.removeEntry(entry);
            }

            // Add the players without a specific team to the remainingTeam
            for (Player player : getServer().getOnlinePlayers().stream().filter(p -> getTeam(p.getUniqueId()) == null).toList()) {
                remainingTeam.addEntry(player.getName());
            }

            List<ITeam> sortedTeams = getTeams();
            if (sortedTeams.stream().allMatch(t -> t.getName().matches("[0-9]*")))
                sortedTeams.sort(Comparator.comparingInt(ITeam::getNameAsNumber));

            for (ITeam team : sortedTeams) {
                org.bukkit.scoreboard.Team bukkitTeam = scoreboard.getTeam(team.getName());

                // If the team does not exist, create it
                if (bukkitTeam == null) {
                    bukkitTeam = scoreboard.registerNewTeam(team.getName());
                }

                // Clear current entries for bukkitTeam to add the correct players later
                for (String entry : new ArrayList<>(bukkitTeam.getEntries())) {
                    bukkitTeam.removeEntry(entry);
                }

                bukkitTeam.prefix(MiniMessageUtil.deserialize(String.format("<%s>[%s] ", team.getColor(), team.getName())));
                bukkitTeam.suffix(Component.empty());
                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
                bukkitTeam.setAllowFriendlyFire(config().getBoolean("allow_friendly_fire"));
                bukkitTeam.setNameTagVisibility(EnumUtil.fetchEnum(NameTagVisibility.class, config.getString("name_tag_visibility"), NameTagVisibility.ALWAYS));

                // Add the players to the respective bukkitTeam
                for (ITeamMember teamMember : team.getMembers()) {
                    Player player = getServer().getPlayer(teamMember.getUniqueId());
                    if (player == null) continue; // player is offline;
                    bukkitTeam.addEntry(player.getName());
                }
            }
        }
    }

    @Override
    public ITeam createTeam(String name) throws TeamAlreadyExistsException {
        if (teams.stream().anyMatch(t -> t.getName().equalsIgnoreCase(name))) throw new TeamAlreadyExistsException();
        Team team = new Team(name, "#FFFFFF");
        teams.add(team);
        return team;
    }

    @Override
    public ITeam deleteTeam(String name) throws TeamNotFoundException {
        ITeam team = teams.stream().filter(t -> t.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (team == null) throw new TeamNotFoundException();
        teams.removeIf(t -> t.getName().equals(team.getName()));
        return team;
    }

    @Override
    public List<ITeam> getTeams() {
        return teams;
    }
}
