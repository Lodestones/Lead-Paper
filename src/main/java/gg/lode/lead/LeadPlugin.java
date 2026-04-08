package gg.lode.lead;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import gg.lode.bookshelfapi.BookshelfAPI;
import gg.lode.bookshelfapi.api.Configuration;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.VersionUpdater;
import gg.lode.bookshelfapi.api.util.EnumHelper;
import gg.lode.bookshelfapi.api.util.Metrics;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.lead.command.LeadCommand;
import gg.lode.lead.command.TeamCommand;
import gg.lode.lead.command.TeamMessageCommand;
import gg.lode.lead.listener.PlayerListener;
import gg.lode.lead.listener.WorldListener;
import gg.lode.lead.listener.chat.BookshelfChatListener;
import gg.lode.lead.listener.chat.SpigotChatListener;
import gg.lode.lead.team.Team;
import gg.lode.lead.team.TeamAlignment;
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

    public String getVersion() {
        return "v" + getDescription().getVersion();
    }
    private static final int CONFIG_VERSION = 15;
    private static final String TEAMLESS_ID = "TEAMLESS";
    public static Random SEED = new Random();
    private final HashMap<UUID, ITeam> teams = new HashMap<>();
    private final HashMap<String, UUID> teamByPlayer = new HashMap<>();
    private final HashMap<String, List<UUID>> playersByTeam = new HashMap<>();
    private final HashMap<String, ITeam> teamsById = new HashMap<>();
    private final HashMap<UUID, String> textureCache = new HashMap<>();
    private final Object saveLock = new Object();
    private Configuration config;
    private Configuration team;
    private Configuration random;
    private Configuration textures;
    private boolean isTABPresent;

    @Override
    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIPaperConfig(this).silentLogs(true).setNamespace("minecraft").fallbackToLatestNMS(true));

        this.team = new Configuration(this, "teams.yml");
        this.team.initialize();

        this.config = new Configuration(this, "config.yml");
        this.config.initialize();

        this.random = new Configuration(this, "random.yml");
        this.random.initialize();

        this.textures = new Configuration(this, "textures.yml");
        this.textures.initialize();

        LeadAPI.setApi(this);
        updateConfigToLatest();
    }

    private void updateConfigToLatest() {
        if (config.getInt("version") < CONFIG_VERSION) {
            getLogger().info("Updating configuration to the latest version...");
            switch (config.getInt("version")) {
                case 12 -> {
                    config.set("nameable_teams", true);
                    config.set("maximum_team_name_length", 16);
                    config.set("minimum_team_name_length", 3);
                }
                case 13 -> config.set("team_alignment", TeamAlignment.PREFIX.name());
                case 14 -> config.set("should_update", true);
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

        if (getServer().getPluginManager().isPluginEnabled("Bookshelf")) {
            getServer().getPluginManager().registerEvents(new BookshelfChatListener(this), this);
            getLogger().warning("Hooked into Bookshelf's Chat Manager!");
            getLogger().warning("==========================================");
        }
        else {
            getServer().getPluginManager().registerEvents(new SpigotChatListener(this), this);
            getLogger().warning("Hooked into Spigot's Chat Manager!");
            getLogger().warning("==========================================");
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new VersionUpdater(this, "Lead", "https://lode.gg/plugin/lead", "https://lode.gg/api/plugins/lead/version", getVersion()), this);

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
            getLogger().severe("==========================================");
            getLogger().severe("TAB is not installed!");
            getLogger().severe("It's okay, Lead will continue to function as best as it can.");
            getLogger().severe(" ");
            getLogger().severe("However, if you do intend on using Lead for over 20+ players,");
            getLogger().severe("I highly recommend installing TAB to remove all of the bugs that come with the vanilla team API.");
            getLogger().severe("https://modrinth.com/plugin/tab-was-taken");
            getLogger().severe("==========================================");
        }
    }

    public Configuration random() {
        return random;
    }

    public void cacheTexture(UUID uuid, String textureValue) {
        textureCache.put(uuid, textureValue);
        textures.set(uuid.toString(), textureValue);
        textures.save();
    }

    @Nullable
    public String getCachedTexture(UUID uuid) {
        return textureCache.get(uuid);
    }

    private void loadTextureCache() {
        textureCache.clear();
        for (String key : textures.get().getKeys(false)) {
            String value = textures.getString(key);
            if (value != null) {
                try {
                    textureCache.put(UUID.fromString(key), value);
                } catch (IllegalArgumentException ignored) {}
            }
        }
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
        synchronized (saveLock) {
            // Wipe all concurrent teams first.
            // Copy keys to avoid ConcurrentModificationException from async access.
            for (String key : new ArrayList<>(team.get().getKeys(false)))
                team.set(key, null);

            for (ITeam team : new ArrayList<>(getTeams())) team.save(this.team.get());
            this.team.save();
        }
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
        int maxTeams = config().getInt("max_teams", 1000);
        List<String> numbers = new ArrayList<>();
        
        // Optimize: Use HashSet for O(1) lookups instead of O(n) getTeam() calls
        Set<String> existingTeamIds = new HashSet<>(teamsById.keySet());
        
        for (int i = 1; i <= maxTeams; i++) {
            String teamId = String.valueOf(i);
            if (!existingTeamIds.contains(teamId)) {
                numbers.add(teamId);
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
        if (!config().getBoolean("should_update", false))
            return;

        long timeNow = System.currentTimeMillis();
        // Prepare all data structures async (no Bukkit API calls)
        Task.runAsync(this, () -> {
            try {
                // Prepare data structures that don't require main thread
                List<ITeam> leadTeams = getTeams();
                Map<UUID, ITeam> playerTeamMap = new HashMap<>();
                Map<String, Set<String>> teamMemberNames = new HashMap<>();
                Set<String> leadTeamIds = new HashSet<>();
                
                for (ITeam team : leadTeams) {
                    leadTeamIds.add(team.getId());
                    Set<String> memberNames = new HashSet<>();
                    for (ITeamMember member : team.getMembers()) {
                        memberNames.add(member.getName());
                        playerTeamMap.put(member.getUniqueId(), team);
                    }
                    teamMemberNames.put(team.getId(), memberNames);
                }
                
                // Cache config values
                String fontToUse = config().getString("font", "default");
                TeamAlignment alignment = EnumHelper.fetchEnum(TeamAlignment.class, config().getString("team_alignment"), TeamAlignment.PREFIX);
                boolean colorNames = config().getBoolean("color_names", false);
                boolean verbose = config().getBoolean("verbose");

                // Now execute all Bukkit/TAB API calls on the main thread
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        if (isTABPresent) {
                            // TAB API calls must be on main thread
                            Class<?> tabClass = Class.forName("me.neznamy.tab.api.TabAPI");
                            Method getInstanceMethod = tabClass.getMethod("getInstance");
                            TabAPI tabApiInstance = (TabAPI) getInstanceMethod.invoke(null);

                            @Nullable NameTagManager nameTagManager = tabApiInstance.getNameTagManager();
                            @Nullable TabListFormatManager tabListFormatManager = tabApiInstance.getTabListFormatManager();

                            // This ensures that nametag or tablist manager is on
                            isTABPresent = nameTagManager != null || tabListFormatManager != null;

                            if (isTABPresent) {
                                for (TabPlayer onlinePlayer : tabApiInstance.getOnlinePlayers()) {
                                    ITeam team = playerTeamMap.get(onlinePlayer.getUniqueId());
                                    if (team == null) {
                                        if (tabListFormatManager != null) tabListFormatManager.setPrefix(onlinePlayer, null);
                                        if (nameTagManager != null) nameTagManager.setPrefix(onlinePlayer, null);
                                        continue;
                                    }

                                    String formattedPrefix = String.format(
                                            "<font:%s><%s>%s</font>%s",
                                            fontToUse,
                                            team.getColor(),
                                            team.getName(),
                                            team.getName().isEmpty() ? "" : String.format((alignment == TeamAlignment.SUFFIX ? " " : "") + ("<%s>") + (alignment == TeamAlignment.PREFIX ? " " : ""), colorNames ? team.getColor() : "white")
                                    );

                                    switch (alignment) {
                                        case SUFFIX -> {
                                            if (nameTagManager != null) nameTagManager.setSuffix(onlinePlayer, formattedPrefix);
                                            if (tabListFormatManager != null) tabListFormatManager.setSuffix(onlinePlayer, formattedPrefix);
                                        }
                                        case PREFIX -> {
                                            if (nameTagManager != null) nameTagManager.setPrefix(onlinePlayer, formattedPrefix);
                                            if (tabListFormatManager != null) tabListFormatManager.setPrefix(onlinePlayer, formattedPrefix); // potential lag?
                                        }
                                    }
                                }
                            }
                        }

                        if (!isTABPresent) {
                            // Scoreboard operations MUST be on main thread
                            List<Player> onlinePlayers = new ArrayList<>(getServer().getOnlinePlayers());
                            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

                            // Create or get a team for players without a specific team
                            org.bukkit.scoreboard.Team remainingTeam = scoreboard.getTeam(TEAMLESS_ID);
                            if (remainingTeam == null) {
                                if (verbose)
                                    Bukkit.broadcast(MiniMessageHelper.deserialize("<gray><italic>[Lead: Creating the remaining team.]"), "lead.debug");
                                remainingTeam = scoreboard.registerNewTeam(TEAMLESS_ID);
                            }

                            remainingTeam.prefix(Component.empty());
                            remainingTeam.suffix(Component.empty());
                            remainingTeam.setAllowFriendlyFire(true);

                            // Remove any players who are in the remaining team but are in a team.
                            for (Player player : onlinePlayers) {
                                if (playerTeamMap.containsKey(player.getUniqueId()) && remainingTeam.hasEntry(player.getName())) {
                                    if (verbose)
                                        Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: Removing %s from the remaining team.]", player.getName())), "lead.debug");
                                    remainingTeam.removeEntry(player.getName());
                                }
                            }

                            // Remove any teams that are no longer a part of the lead team list.
                            for (org.bukkit.scoreboard.Team bukkitTeam : new ArrayList<>(scoreboard.getTeams())) {
                                if (!bukkitTeam.getName().equals(TEAMLESS_ID) && !leadTeamIds.contains(bukkitTeam.getName())) {
                                    if (Bukkit.getScoreboardManager().getMainScoreboard().getTeam(bukkitTeam.getName()) == null)
                                        continue;

                                    if (verbose)
                                        Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: Removing scoreboard team named %s.]", bukkitTeam.getName())), "lead.debug");

                                    bukkitTeam.unregister();
                                }
                            }

                            // Create any teams that haven't been created yet.
                            Set<String> existingTeamIds = new HashSet<>();
                            for (org.bukkit.scoreboard.Team bukkitTeam : scoreboard.getTeams()) {
                                existingTeamIds.add(bukkitTeam.getName());
                            }

                            for (ITeam team : leadTeams) {
                                Objects.requireNonNull(team.getId());
                                if (!existingTeamIds.contains(team.getId())) {
                                    if (verbose)
                                        Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: Creating scoreboard team named %s.]", team.getId())), "lead.debug");

                                    if (scoreboard.getTeam(team.getId()) == null)
                                        scoreboard.registerNewTeam(team.getId());
                                }
                            }

                            // Loop through every team now that we know that these teams exist.
                            for (ITeam team : leadTeams) {
                                Objects.requireNonNull(team.getName());

                                @NotNull org.bukkit.scoreboard.Team bukkitTeam = Objects.requireNonNull(scoreboard.getTeam(team.getId()));
                                switch (alignment) {
                                    case PREFIX -> {
                                        bukkitTeam.prefix(MiniMessageHelper.deserialize(String.format("<%s>%s ", team.getColor(), Objects.requireNonNullElse(team.getName(), team.getId()))));
                                        bukkitTeam.suffix(Component.empty());
                                    }
                                    case SUFFIX -> {
                                        bukkitTeam.suffix(MiniMessageHelper.deserialize(String.format("<%s>%s ", team.getColor(), Objects.requireNonNullElse(team.getName(), team.getId()))));
                                        bukkitTeam.prefix(Component.empty());
                                    }
                                }

                                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, team.getCollidable());
                                bukkitTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, team.getNameTagVisibility());
                                bukkitTeam.setAllowFriendlyFire(team.isFriendlyFireAllowed());

                                // Use cached member names
                                Set<String> leadMemberNames = teamMemberNames.get(team.getId());
                                if (leadMemberNames == null) leadMemberNames = new HashSet<>();

                                // Remove any team members that are no longer a part of the lead team member list.
                                for (String bukkitMember : new HashSet<>(bukkitTeam.getEntries())) {
                                    Objects.requireNonNull(bukkitMember);
                                    if (!leadMemberNames.contains(bukkitMember)) {
                                        Player player = Bukkit.getPlayerExact(bukkitMember);
                                        if (player != null && player.isOnline()) {
                                            if (verbose)
                                                Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: Removing %s from scoreboard team %s.]", bukkitMember, team.getId())), "lead.debug");
                                            bukkitTeam.removeEntry(bukkitMember);
                                        }
                                    }
                                }

                                // Create any team members that haven't been created yet.
                                for (String leadMemberName : leadMemberNames) {
                                    if (!bukkitTeam.hasEntry(leadMemberName)) {
                                        if (verbose)
                                            Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: Adding %s to scoreboard team %s.]", leadMemberName, team.getId())), "lead.debug");
                                        bukkitTeam.addEntry(leadMemberName);
                                    }
                                }
                            }

                            // Add any players who aren't in a team in the remaining team list.
                            for (Player player : onlinePlayers) {
                                if (!playerTeamMap.containsKey(player.getUniqueId()) && !remainingTeam.hasEntry(player.getName())) {
                                    if (verbose)
                                        Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: Adding %s to the remaining team.]", player.getName())), "lead.debug");
                                    remainingTeam.addEntry(player.getName());
                                }
                            }

                            // Only set scoreboard for players whose scoreboard actually changed
                            for (Player plr : onlinePlayers) {
                                if (plr != null && plr.isOnline() && plr.getScoreboard() != scoreboard) {
                                    plr.setScoreboard(scoreboard);
                                }
                            }
                        }
                        
                        if (config().getBoolean("verbose"))
                            Bukkit.broadcast(MiniMessageHelper.deserialize(String.format("<gray><italic>[Lead: Updated all teams in %s ms.]", System.currentTimeMillis() - timeNow)), "lead.debug");
                    } catch (Exception e) {
                        e.printStackTrace();
                        getLogger().warning("==========================================");
                        getLogger().warning("Uh oh. An error!");
                        getLogger().warning("Using the built-in vanilla team api comes with stupid bugs.");
                        getLogger().warning("I recommend installing TAB to remove all of these bugs as a whole");
                        getLogger().warning("https://modrinth.com/plugin/tab-was-taken");
                        getLogger().warning("==========================================");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
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
                        EnumHelper.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, section.getString("collidable"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS),
                        EnumHelper.fetchEnum(org.bukkit.scoreboard.Team.OptionStatus.class, section.getString("name_tag_visibility"), org.bukkit.scoreboard.Team.OptionStatus.ALWAYS),
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

        loadTextureCache();

        for (Player player : getServer().getOnlinePlayers()) {
            CommandAPI.updateRequirements(player);
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
