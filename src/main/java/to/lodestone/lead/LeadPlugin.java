package to.lodestone.lead;

import dev.jorel.commandapi.CommandAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
import to.lodestone.bookshelfapi.api.PremiumManager;
import to.lodestone.bookshelfapi.api.Task;
import to.lodestone.bookshelfapi.api.VersionUpdater;
import to.lodestone.bookshelfapi.api.util.EnumUtil;
import to.lodestone.bookshelfapi.api.util.Metrics;
import to.lodestone.bookshelfapi.api.util.MiniMessageUtil;
import to.lodestone.lead.command.LeadCommand;
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
import to.lodestone.leadapi.api.exception.MaxTeamLimitException;
import to.lodestone.leadapi.api.exception.TeamAlreadyExistsException;
import to.lodestone.leadapi.api.exception.TeamNotFoundException;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

public final class LeadPlugin extends JavaPlugin implements ILeadAPI {

    public static final String VERSION = "v1.1.8";
    private static final int CONFIG_VERSION = 5;
    private static final String TEAMLESS_ID = "TEAMLESS";

    private final HashMap<UUID, ITeam> teams = new HashMap<>();
    private final HashMap<String, UUID> teamByPlayer = new HashMap<>();
    private final HashMap<String, List<UUID>> playersByTeam = new HashMap<>();
    private final HashMap<String, ITeam> teamsById = new HashMap<>();

    public static Random SEED = new Random();
    private Configuration config;
    private Configuration team;
    private Configuration random;
    private PremiumManager premiumManager;

    @Override
    public void onLoad() {
        this.team = new Configuration(this, "teams.yml");
        this.team.initialize();

        this.config = new Configuration(this, "config.yml");
        this.config.initialize();

        this.random = new Configuration(this, "random.yml");
        this.random.initialize();
    }

    @Override
    public void onEnable() {
        new Metrics(this, 22603); // bStats

        LeadAPI.setApi(this);

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

        this.reload(true);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new VersionUpdater(this, "Lead", "https://modrinth.com/plugin/lead", "https://api.modrinth.com/v2/project/lead/version", VERSION), this);

        this.premiumManager = new PremiumManager(this);
    }

    public Configuration random() {
        return random;
    }

    public boolean isPremiumServer() {
        return this.premiumManager.isPremiumServer();
    }

    public IBookshelfAPI bookshelf() {
        return BookshelfAPI.getApi();
    }

    private void registerCommands() {
        CommandAPI.unregister("team", true);
        CommandAPI.unregister("teammsg", true);

        new LeadCommand(this).register();
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
        return teams.get(teamByPlayer.get(member.toString()));
    }

    @Nullable
    public ITeam getTeam(String id) {
        return teamsById.get(id);
    }

    @Override
    public boolean hasTeam(UUID member) {
        return getTeam(member) != null;
    }

    public String getAvailableTeamNumber() {
        List<String> numbers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            if (getTeam(String.valueOf(i)) == null) {
                numbers.add(String.valueOf(i));
            }
        }

        if (numbers.size() == 0)
            throw new IllegalStateException("No available team number!");

        Collections.shuffle(numbers, SEED);
        return numbers.get(0);
    }

    @Override
    public void update() {
        long timeNow = System.currentTimeMillis();
        Task.runAsync(this, () -> {
            // Loop through the entire list.
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

            // Create or get a team for players without a specific team
            org.bukkit.scoreboard.Team remainingTeam = scoreboard.getTeam(TEAMLESS_ID);
            if (remainingTeam == null) {
                if (config().getBoolean("debug"))
                    Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Creating the remaining team.]"), "lead.debug");
                remainingTeam = scoreboard.registerNewTeam(TEAMLESS_ID);
            }

            remainingTeam.prefix(Component.empty());
            remainingTeam.suffix(Component.empty());
            remainingTeam.setAllowFriendlyFire(true);

            // Remove any players who are in the remaining team but are in a team.
            for (Player player : getServer().getOnlinePlayers().stream().filter(player -> hasTeam(player.getUniqueId())).toList()) {
                if (remainingTeam.hasEntry(player.getName())) {
                    if (config().getBoolean("debug"))
                        Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Removing %s from the remaining team.]", player.getName()), "lead.debug");
                    remainingTeam.removeEntry(player.getName());
                }
            }

            // Remove any teams that are no longer a part of the lead team list.
            for (org.bukkit.scoreboard.Team bukkitTeam : scoreboard.getTeams().stream().filter(bukkitTeam -> !bukkitTeam.getName().equals(TEAMLESS_ID) && getTeam(bukkitTeam.getName()) == null).toList()) {
                if (config().getBoolean("debug"))
                    Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Removing scoreboard team named %s.]", bukkitTeam.getName()), "lead.debug");
                bukkitTeam.unregister();
            }

            // Create any teams that haven't been created yet.
            for (ITeam team : getTeams().stream().filter(leadTeam -> scoreboard.getTeams().stream().noneMatch(bukkitTeam -> bukkitTeam.getName().equals(leadTeam.getId())))
                    .toList()) {
                if (config().getBoolean("debug"))
                    Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Creating scoreboard team named %s.]", team.getId()), "lead.debug");
                scoreboard.registerNewTeam(team.getId());
            }

            // Loop through every team now that we know that these teams exist.
            for (ITeam team : getTeams()) {
                @NotNull org.bukkit.scoreboard.Team bukkitTeam = Objects.requireNonNull(scoreboard.getTeam(team.getId()));
                bukkitTeam.prefix(MiniMessageUtil.deserialize(String.format("<%s>%s ", team.getColor(), Objects.requireNonNullElse(team.getName(), team.getId()))));
                bukkitTeam.suffix(Component.empty());
                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, team.getCollidable());
                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, team.getNameTagVisibility());
                bukkitTeam.setAllowFriendlyFire(team.isFriendlyFireAllowed());

                // Remove any team members that are no longer a part of the lead team member list.
                for (String bukkitMember : bukkitTeam.getEntries()) {
                    if (team.getMembers().stream().noneMatch(leadMember -> leadMember.getName().equals(bukkitMember))) {
                        if (config().getBoolean("debug"))
                            Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Removing %s from scoreboard team %s.]", bukkitMember, team.getId()), "lead.debug");
                        bukkitTeam.removeEntry(bukkitMember);
                    }
                }

                // Create any team members that haven't been created yet.
                for (ITeamMember leadMember : team.getMembers().stream().filter(leadMember -> !bukkitTeam.hasEntry(leadMember.getName())).toList()) {
                    if (config().getBoolean("debug"))
                        Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Adding %s to scoreboard team %s.]", leadMember.getName(), team.getId()), "lead.debug");
                    bukkitTeam.addEntry(leadMember.getName());
                }
            }

            // Add any players who aren't in a team in the remaining team list.
            for (Player player : getServer().getOnlinePlayers().stream().filter(player -> !hasTeam(player.getUniqueId())).toList()) {
                if (!remainingTeam.hasEntry(player.getName())) {
                    if (config().getBoolean("debug"))
                        Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Adding %s to the remaining team.]", player.getName()), "lead.debug");
                    remainingTeam.addEntry(player.getName());
                }
            }

            for (Player plr : getServer().getOnlinePlayers())
                plr.setScoreboard(scoreboard);

            if (config().getBoolean("debug"))
                Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Updated all teams in %s ms.]", System.currentTimeMillis() - timeNow), "lead.debug");
        });
    }

    @Override
    public ITeam createTeam(String id, UUID leader, String color) throws TeamAlreadyExistsException {
        if (getTeam(id) != null) throw new TeamAlreadyExistsException();
        Team team = new Team(this, id, leader, color);
        teams.put(team.getUniqueId(), team);
        teamsById.put(team.getId(), team);
        playersByTeam.put(team.getUniqueId().toString(), new ArrayList<>());
        return team;
    }

    @Override
    public ITeam createTeam(String id) throws TeamAlreadyExistsException {
        if (getTeam(id) != null) throw new TeamAlreadyExistsException();
        List<String> randomColors = random().getStringList("available_hex_colors");
        Team team = new Team(this, id, randomColors.size() == 0 ? "#FFFFFF" : randomColors.get(SEED.nextInt(randomColors.size())));
        teams.put(team.getUniqueId(), team);
        teamsById.put(team.getId(), team);
        playersByTeam.put(team.getUniqueId().toString(), new ArrayList<>());
        return team;
    }

    @Override
    public ITeam createTeam(String id, UUID leader) throws TeamAlreadyExistsException {
        if (getTeam(id) != null) throw new TeamAlreadyExistsException();
        List<String> randomColors = random().getStringList("available_hex_colors");
        Team team = new Team(this, id, leader, randomColors.size() == 0 ? "#FFFFFF" : randomColors.get(SEED.nextInt(randomColors.size())));
        teams.put(team.getUniqueId(), team);
        teamsById.put(team.getId(), team);
        playersByTeam.put(team.getUniqueId().toString(), new ArrayList<>());
        return team;
    }

    public void reload(boolean reloadTeams) {
        if (reloadTeams) {
            this.teamByPlayer.clear();
            this.teams.clear();
            this.teamsById.clear();
            this.playersByTeam.clear();

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

                UUID leaderUniqueId = section.getString("leaderUniqueId") == null ? null : UUID.fromString(Objects.requireNonNull(section.getString("leaderUniqueId")));

                Team team = new Team(
                        this,
                        section.getString("id", section.getString("name")),
                        section.getString("name"),
                        section.getString("color", "#FFFFFF"),
                        UUID.fromString(teamUniqueId),
                        leaderUniqueId,
                        members,
                        new ArrayList<>(section.getStringList("invitations").stream().map(UUID::fromString).toList()),
                        EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, section.getString("collidable"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS),
                        EnumUtil.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, section.getString("name_tag_visibility"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS),
                        section.getBoolean("is_friendly_fire_allowed", true)
                );
                teams.put(team.getUniqueId(), team);
            }

            // Populate playersByTeam, teamByPlayer, teamsById, and teamsByUniqueId
            for (ITeam team : teams.values()) {
                List<UUID> playerIds = new ArrayList<>();
                for (ITeamMember member : team.getMembers()) {
                    UUID playerId = member.getUniqueId();
                    playerIds.add(playerId);
                    teamByPlayer.put(playerId.toString(), team.getUniqueId());
                }
                playersByTeam.put(team.getUniqueId().toString(), playerIds);
                teamsById.put(team.getId(), team);
            }
        }

        this.config.initialize();
        this.random.initialize();
    }

    @Override
    public ITeam deleteTeam(String id) throws TeamNotFoundException {
        ITeam team = getTeam(id);
        if (team == null) throw new TeamNotFoundException();
        teams.remove(team.getUniqueId());
        teamsById.remove(team.getId());
        playersByTeam.remove(team.getUniqueId().toString());
        team.getMembers().forEach(member -> teamByPlayer.remove(member.getUniqueId().toString()));
        return team;
    }

    @Override
    public void removePlayerFromTeam(ITeam team, UUID player) {
        team.removeMember(player);
        List<UUID> players = new ArrayList<>(playersByTeam.get(team.getUniqueId().toString()));
        players.remove(player);
        playersByTeam.put(team.getUniqueId().toString(), players);
        teamByPlayer.remove(player.toString());
    }

    @Override
    public List<ITeam> getTeams() {
        return teams.values().stream().toList();
    }

    @Override
    public ITeam deleteTeam(ITeam team) {
        teams.remove(team.getUniqueId());
        teamsById.remove(team.getId());
        playersByTeam.remove(team.getUniqueId().toString());
        team.getMembers().forEach(member -> teamByPlayer.remove(member.getUniqueId().toString()));
        return team;
    }

    public HashMap<String, ITeam> getTeamsById() {
        return teamsById;
    }

    public HashMap<String, List<UUID>> getPlayersByTeam() {
        return playersByTeam;
    }

    public HashMap<String, UUID> getTeamByPlayer() {
        return teamByPlayer;
    }

    public Configuration teams() {
        return team;
    }
}
