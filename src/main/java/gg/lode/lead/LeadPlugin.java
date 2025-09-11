package gg.lode.lead;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import gg.lode.bookshelfapi.BookshelfAPI;
import gg.lode.bookshelfapi.api.Configuration;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.VersionUpdater;
import gg.lode.bookshelfapi.api.util.EnumUtil;
import gg.lode.bookshelfapi.api.util.Metrics;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.lead.command.LeadCommand;
import gg.lode.lead.command.TeamCommand;
import gg.lode.lead.command.TeamMessageCommand;
import gg.lode.lead.listener.PlayerListener;
import gg.lode.lead.listener.WorldListener;
import gg.lode.lead.listener.chat.BookshelfChatListener;
import gg.lode.lead.listener.chat.SpigotChatListener;
import gg.lode.lead.team.Team;
import gg.lode.lead.team.TeamMember;
import gg.lode.leadapi.ILeadAPI;
import gg.lode.leadapi.LeadAPI;
import gg.lode.leadapi.api.GeneratorType;
import gg.lode.leadapi.api.ITeam;
import gg.lode.leadapi.api.ITeamMember;
import gg.lode.leadapi.api.event.TeamCreateEvent;
import gg.lode.leadapi.api.event.TeamDeleteEvent;
import gg.lode.leadapi.api.event.TeamRemoveEvent;
import gg.lode.leadapi.api.exception.MaxTeamLimitException;
import gg.lode.leadapi.api.exception.TeamAlreadyExistsException;
import gg.lode.leadapi.api.exception.TeamNotFoundException;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.plugin.TabLoadEvent;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.neznamy.tab.api.tablist.TabListFormatManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public final class LeadPlugin extends JavaPlugin implements ILeadAPI {

    public static final String VERSION = "v1.2.5";
    private static final int CONFIG_VERSION = 12;
    private static final String TEAMLESS_ID = "TEAMLESS";
    public static Random SEED = new Random();
    private final HashMap<UUID, ITeam> teams = new HashMap<>();
    private final HashMap<String, UUID> teamByPlayer = new HashMap<>();
    private final HashMap<String, List<UUID>> playersByTeam = new HashMap<>();
    private final HashMap<String, ITeam> teamsById = new HashMap<>();
    private Configuration config;
    private Configuration team;
    private Configuration random;
    private boolean isTABPresent;

    @Override
    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this).silentLogs(true).setNamespace("minecraft").shouldHookPaperReload(true));

        this.team = new Configuration(this, "teams.yml");
        this.team.initialize();

        this.config = new Configuration(this, "config.yml");
        this.config.initialize();

        this.random = new Configuration(this, "random.yml");
        this.random.initialize();

        LeadAPI.setApi(this);
        updateConfigToLatest();
    }

    private void updateConfigToLatest() {
        if (config.getInt("version") < CONFIG_VERSION) {
            getLogger().info("Updating configuration to the latest version...");
            if (config.getInt("version") == 12) {
                config.set("nameable_teams", true);
                config.set("minimum_team_name_length", 3);
                config.set("maximum_team_name_length", 16);
            }

            // Recursively call this method to ensure all updates are applied
            config.set("version", config.getInt("version") + 1);
            if (config.getInt("version") < CONFIG_VERSION) {
                updateConfigToLatest();
            } else {
                config.save();
                getLogger().info("Configuration updated to the latest version successfully.");
            }
        }
    }

    @Override
    public void onEnable() {
        CommandAPI.onEnable();
        BookshelfAPI.init(this, BookshelfAPI.Builder.createDisabled()
                .useMenuManager(true));

        new Metrics(this, 22603); // bStats

        if (config().getInt("version") != CONFIG_VERSION) {
            getLogger().severe("==========================================");
            getLogger().severe("OUTDATED CONFIGURATION FILE");
            getLogger().severe("Your configuration file is outdated. Please delete your config.yml and restart the server.");
            getLogger().severe("Lead may not function properly because of this!");
            getLogger().severe("==========================================");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().warning("==========================================");
            getLogger().warning("Hooked into PlaceholderAPI!");
            getLogger().warning("Lead will now use PlaceholderAPI to display team information.");
            getLogger().warning("Use %lead_team_id% to fetch the player's team id.");
            getLogger().warning("Use %lead_team_name% to fetch the player's team name.");
            getLogger().warning("==========================================");
            new PlaceholderManager(this).register();
        }

        this.registerCommands();
        this.reload(true);

        if (getServer().getPluginManager().isPluginEnabled("Bookshelf"))
            getServer().getPluginManager().registerEvents(new BookshelfChatListener(this), this);
        else getServer().getPluginManager().registerEvents(new SpigotChatListener(this), this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new VersionUpdater(this, "Lead", "https://lode.gg/plugin/lead", "https://lode.gg/api/plugins/lead/version", VERSION), this);

        this.isTABPresent = getServer().getPluginManager().isPluginEnabled("TAB");
        if (this.isTABPresent) {
            getLogger().warning("==========================================");
            getLogger().warning("Hooked into TAB!");
            getLogger().warning("Lead will now use TAB to display teams.");
            getLogger().warning("==========================================");

            try {
                Class<?> tabClass = Class.forName("me.neznamy.tab.api.TabAPI");
                Method getInstanceMethod = tabClass.getMethod("getInstance");
                TabAPI tabApiInstance = (TabAPI) getInstanceMethod.invoke(null);

                Objects.requireNonNull(tabApiInstance.getEventBus()).register(TabLoadEvent.class, event -> this.update());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            getLogger().warning("==========================================");
            getLogger().warning("TAB is not installed!");
            getLogger().warning("It's okay, Lead will continue to function as best as it can.");
            getLogger().warning("Unfortunately, if you do intend on using Lead for mass players/teams,");
            getLogger().warning("I highly recommend installing TAB to remove all of the bugs that come with the vanilla team API.");
            getLogger().warning("https://modrinth.com/plugin/tab-was-taken");
            getLogger().warning("==========================================");
        }
    }

    public Configuration random() {
        return random;
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

    @Override
    public String getAvailableTeamNumber() {
        List<String> numbers = new ArrayList<>();
        for (int i = 1; i <= config().getInt("max_teams", 1000); i++) {
            if (getTeam(String.valueOf(i)) == null) {
                numbers.add(String.valueOf(i));
            }
        }

        if (numbers.size() == 0)
            throw new IllegalStateException("No available team number!");

        if (!config().getBoolean("should_increment", false))
            Collections.shuffle(numbers, SEED);

        return numbers.get(0);
    }

    @Override
    public void update() {
        long timeNow = System.currentTimeMillis();
        Task.runAsync(this, () -> {
                if (isTABPresent) {
                    try {
                        Class<?> tabClass = Class.forName("me.neznamy.tab.api.TabAPI");
                        Method getInstanceMethod = tabClass.getMethod("getInstance");
                        TabAPI tabApiInstance = (TabAPI) getInstanceMethod.invoke(null);

                        for (TabPlayer onlinePlayer : tabApiInstance.getOnlinePlayers()) {
                            @Nullable ITeam team = getTeam(onlinePlayer.getUniqueId());
                            if (team == null) {
                                TabListFormatManager tabListFormatManager = tabApiInstance.getTabListFormatManager();
                                if (tabListFormatManager != null) tabListFormatManager.setPrefix(onlinePlayer, null);

                                NameTagManager nameTagManager = tabApiInstance.getNameTagManager();
                                if (nameTagManager != null) nameTagManager.setPrefix(onlinePlayer, null);
                                continue;
                            }

                            // max 16 characters
                            String fontToUse = config().getString("font", "default");
//                        Objects.requireNonNull(tabApiInstance.getSortingManager()).forceTeamName(onlinePlayer, team.getId().substring(0, Math.min(16, team.getId().length())));

                            String formattedPrefix = String.format(
                                    "<font:%s><%s>%s</font>%s",
                                    fontToUse,
                                    team.getColor(),
                                    team.getName(),
                                    team.getName().isEmpty() ? "" : String.format("<%s> ", config().getBoolean("color_names", false) ? team.getColor() : "white")
                            );

                            Objects.requireNonNull(tabApiInstance.getNameTagManager()).setPrefix(onlinePlayer, formattedPrefix);
                            Objects.requireNonNull(tabApiInstance.getTabListFormatManager()).setPrefix(onlinePlayer, formattedPrefix);
                        }
                    } catch (Exception err) {
                        err.printStackTrace();
                    }
                } else {
                    try {
                        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

                        // Create or get a team for players without a specific team
                        org.bukkit.scoreboard.Team remainingTeam = scoreboard.getTeam(TEAMLESS_ID);
                        if (remainingTeam == null) {
                            if (config().getBoolean("verbose"))
                                Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Creating the remaining team.]"), "lead.debug");
                            remainingTeam = scoreboard.registerNewTeam(TEAMLESS_ID);
                        }

                        remainingTeam.prefix(Component.empty());
                        remainingTeam.suffix(Component.empty());
                        remainingTeam.setAllowFriendlyFire(true);

                        // Remove any players who are in the remaining team but are in a team.
                        for (Player player : getServer().getOnlinePlayers().stream().filter(player -> hasTeam(player.getUniqueId())).toList()) {
                            if (remainingTeam.hasEntry(player.getName())) {
                                if (config().getBoolean("verbose"))
                                    Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Removing %s from the remaining team.]", player.getName()), "lead.debug");
                                remainingTeam.removeEntry(player.getName());
                            }
                        }

                        // Remove any teams that are no longer a part of the lead team list.
                        for (org.bukkit.scoreboard.Team bukkitTeam : scoreboard.getTeams().stream().filter(bukkitTeam -> !bukkitTeam.getName().equals(TEAMLESS_ID) && getTeam(bukkitTeam.getName()) == null).toList()) {
                            if (Bukkit.getScoreboardManager().getMainScoreboard().getTeam(bukkitTeam.getName()) == null)
                                continue;

                            if (config().getBoolean("verbose"))
                                Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Removing scoreboard team named %s.]", bukkitTeam.getName()), "lead.debug");

                            bukkitTeam.unregister();
                        }

                        // Create any teams that haven't been created yet.
                        for (ITeam team : getTeams().stream().filter(leadTeam -> scoreboard.getTeams().stream().noneMatch(bukkitTeam -> bukkitTeam.getName().equals(leadTeam.getId()))).toList()) {
                            Objects.requireNonNull(team.getId());
                            if (config().getBoolean("verbose"))
                                Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Creating scoreboard team named %s.]", team.getId()), "lead.debug");

                            if (scoreboard.getTeam(team.getId()) == null)
                                scoreboard.registerNewTeam(team.getId());
                        }

                        // Loop through every team now that we know that these teams exist.
                        for (ITeam team : getTeams()) {
                            Objects.requireNonNull(team.getName());

                            @NotNull org.bukkit.scoreboard.Team bukkitTeam = Objects.requireNonNull(scoreboard.getTeam(team.getId()));
                            bukkitTeam.prefix(MiniMessageUtil.deserialize(String.format("<%s>%s ", team.getColor(), Objects.requireNonNullElse(team.getName(), team.getId()))));
                            bukkitTeam.suffix(Component.empty());
                            bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, team.getCollidable());
                            bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, team.getNameTagVisibility());
                            bukkitTeam.setAllowFriendlyFire(team.isFriendlyFireAllowed());

                            // Remove any team members that are no longer a part of the lead team member list.
                            for (String bukkitMember : new HashSet<>(bukkitTeam.getEntries())) {
                                Objects.requireNonNull(bukkitMember);
                                if (team.getMembers().stream().noneMatch(leadMember -> leadMember.getName().equals(bukkitMember))) {
                                    Player player = Bukkit.getPlayerExact(bukkitMember);
                                    if (player != null && player.isOnline()) {
                                        if (config().getBoolean("verbose"))
                                            Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Removing %s from scoreboard team %s.]", bukkitMember, team.getId()), "lead.debug");
                                        bukkitTeam.removeEntry(bukkitMember);
                                    }
                                }
                            }

                            // Create any team members that haven't been created yet.
                            for (ITeamMember leadMember : team.getMembers().stream().filter(leadMember -> !bukkitTeam.hasEntry(leadMember.getName())).toList()) {
                                Objects.requireNonNull(leadMember.getName());
                                if (config().getBoolean("verbose"))
                                    Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Adding %s to scoreboard team %s.]", leadMember.getName(), team.getId()), "lead.debug");
                                bukkitTeam.addEntry(leadMember.getName());
                            }
                        }

                        // Add any players who aren't in a team in the remaining team list.
                        for (Player player : getServer().getOnlinePlayers().stream().filter(player -> !hasTeam(player.getUniqueId())).toList()) {
                            if (!remainingTeam.hasEntry(player.getName())) {
                                if (config().getBoolean("verbose"))
                                    Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Adding %s to the remaining team.]", player.getName()), "lead.debug");
                                remainingTeam.addEntry(player.getName());
                            }
                        }

                        // Set the scoreboard on the main thread to avoid client sync issues
                        Bukkit.getScheduler().runTask(this, () -> {
                            for (Player plr : getServer().getOnlinePlayers()) {
                                if (plr != null && plr.isOnline()) {
                                    plr.setScoreboard(scoreboard);
                                }
                            }
                        });

                        if (config().getBoolean("verbose"))
                            Bukkit.broadcast(MiniMessageUtil.deserialize("<gray><italic>[Lead: Updated all teams in %s ms.]", System.currentTimeMillis() - timeNow), "lead.debug");
                    } catch (Exception e) {
                        e.printStackTrace();
                        getLogger().warning("==========================================");
                        getLogger().warning("Uh oh. An error!");
                        getLogger().warning("Using the built-in vanilla team api comes with stupid bugs.");
                        getLogger().warning("I recommend installing TAB to remove all of these bugs as a whole");
                        getLogger().warning("https://modrinth.com/plugin/tab-was-taken");
                        getLogger().warning("==========================================");
                    }
                }
        });
    }

    @Override
    public ITeam createTeamWithUniqueColor(Player player, String id, String name) throws MaxTeamLimitException, TeamAlreadyExistsException {
        ConfigurationSection section = random().getConfigurationSection("connected_colors");
        if (section == null) throw new MaxTeamLimitException();
        Map<String, Object> teamColors = section.getValues(false);
        @SuppressWarnings("unchecked")
        Map.Entry<String, Object> entry = (Map.Entry<String, Object>) teamColors.entrySet().toArray()[LeadPlugin.SEED.nextInt(teamColors.size())];
        ITeam iTeam = createTeamByColor(id, player.getUniqueId(), entry.getKey());
        iTeam.setName(name);
        iTeam.addMember(player);
        new TeamCreateEvent(iTeam, player).callEvent();
        return iTeam;
    }

    @Override
    public ITeam createTeamByType(Player player, String name, GeneratorType generatorType) throws MaxTeamLimitException, TeamAlreadyExistsException {
        ITeam iTeam = switch (generatorType) {
            case NUMBER -> {
                String number = getAvailableTeamNumber();
                yield createTeamWithLeader(number, player.getUniqueId());
            }
            case NAME -> {
                List<String> teamNames = random().getStringList("available_names");
                yield createTeamWithLeader(teamNames.get(LeadPlugin.SEED.nextInt(teamNames.size())), player.getUniqueId());
            }
            case COLOR -> {
                ConfigurationSection section = random().getConfigurationSection("connected_colors");
                if (section == null) throw new MaxTeamLimitException();
                Map<String, Object> teamColors = section.getValues(false);
                @SuppressWarnings("unchecked")
                Map.Entry<String, Object> entry = (Map.Entry<String, Object>) teamColors.entrySet().toArray()[LeadPlugin.SEED.nextInt(teamColors.size())];
                yield createTeamByColor((String) entry.getValue(), player.getUniqueId(), entry.getKey());
            }
            case UNICODE -> {
                String unicode = random().getString("unicode");
                yield createTeamWithLeader(unicode, player.getUniqueId());
            }
        };

        iTeam.setName(name);
        iTeam.addMember(player);
        new TeamCreateEvent(iTeam, player).callEvent();
        return iTeam;
    }

    @Override
    public ITeam createTeamByType(Player player, GeneratorType teamType) throws MaxTeamLimitException, TeamAlreadyExistsException {
        ITeam iTeam = switch (teamType) {
            case NUMBER -> {
                String number = getAvailableTeamNumber();
                yield createTeamWithLeader(number, player.getUniqueId());
            }
            case NAME -> {
                List<String> teamNames = random().getStringList("available_names");
                yield createTeamWithLeader(teamNames.get(LeadPlugin.SEED.nextInt(teamNames.size())), player.getUniqueId());
            }
            case COLOR -> {
                ConfigurationSection section = random().getConfigurationSection("connected_colors");
                if (section == null) throw new MaxTeamLimitException();
                Map<String, Object> teamColors = section.getValues(false);
                @SuppressWarnings("unchecked")
                Map.Entry<String, Object> entry = (Map.Entry<String, Object>) teamColors.entrySet().toArray()[LeadPlugin.SEED.nextInt(teamColors.size())];
                yield createTeamByColor((String) entry.getValue(), player.getUniqueId(), entry.getKey());
            }
            case UNICODE -> {
                String unicode = random().getString("unicode");
                yield createTeamWithLeader(unicode, player.getUniqueId());
            }
        };

        iTeam.addMember(player);
        new TeamCreateEvent(iTeam, player).callEvent();
        return iTeam;
    }

    public boolean isTABPresent() {
        return isTABPresent;
    }

    @Override
    public ITeam createTeamByColor(String id, UUID leader, String color) throws TeamAlreadyExistsException {
        if (getTeam(id) != null) throw new TeamAlreadyExistsException();
        Team team = new Team(this, id, leader, color);
        teams.put(team.getUniqueId(), team);
        teamsById.put(team.getId(), team);
        playersByTeam.put(team.getUniqueId().toString(), new ArrayList<>());
        Player player = getServer().getPlayer(leader);
        if (player != null) team.addMember(player);
        new TeamCreateEvent(team, player).callEvent();
        return team;
    }

    @Override
    public ITeam createTeamById(String id) throws TeamAlreadyExistsException {
        if (getTeam(id) != null) throw new TeamAlreadyExistsException();
        List<String> randomColors = random().getStringList("available_hex_colors");
        Team team = new Team(this, id, randomColors.size() == 0 ? "#FFFFFF" : randomColors.get(SEED.nextInt(randomColors.size())));
        teams.put(team.getUniqueId(), team);
        teamsById.put(team.getId(), team);
        playersByTeam.put(team.getUniqueId().toString(), new ArrayList<>());
        new TeamCreateEvent(team, null).callEvent();
        return team;
    }

    @Override
    public ITeam createTeamWithLeader(String id, UUID leader) throws TeamAlreadyExistsException {
        if (getTeam(id) != null) throw new TeamAlreadyExistsException();
        List<String> randomColors = random().getStringList("available_hex_colors");
        Team team = new Team(this, id, leader, randomColors.size() == 0 ? "#FFFFFF" : randomColors.get(SEED.nextInt(randomColors.size())));
        teams.put(team.getUniqueId(), team);
        teamsById.put(team.getId(), team);
        playersByTeam.put(team.getUniqueId().toString(), new ArrayList<>());
        Player player = getServer().getPlayer(leader);
        if (player != null) team.addMember(player);
        new TeamCreateEvent(team, player).callEvent();
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
                Location spawnLocation = null;
                if (section.getString("spawn_location") != null) {
                    String[] location = Objects.requireNonNull(section.getString("spawn_location")).split(",");
                    spawnLocation = new Location(
                            Bukkit.getWorld(location[0]),
                            Double.parseDouble(location[1]),
                            Double.parseDouble(location[2]),
                            Double.parseDouble(location[3]),
                            Float.parseFloat(location[4]),
                            Float.parseFloat(location[5])
                    );
                }

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
                        section.getBoolean("is_friendly_fire_allowed", true),
                        spawnLocation
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
        new TeamDeleteEvent(team).callEvent();
        return team;
    }

    @Override
    public void removePlayerFromTeam(ITeam team, UUID player) {
        team.removeMember(player);
        List<UUID> players = new ArrayList<>(playersByTeam.get(team.getUniqueId().toString()));
        players.remove(player);
        playersByTeam.put(team.getUniqueId().toString(), players);
        teamByPlayer.remove(player.toString());
        new TeamRemoveEvent(team, UUID.fromString(player.toString())).callEvent();
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
        new TeamDeleteEvent(team).callEvent();
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
