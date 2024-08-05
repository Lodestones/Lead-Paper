package to.lodestone.lead.team;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import to.lodestone.leadapi.api.ITeam;
import to.lodestone.leadapi.api.ITeamMember;

import java.util.*;

public class Team implements ITeam {

    private String id;
    private String name;
    private final UUID uniqueId;
    private final ArrayList<ITeamMember> members;
    private final ArrayList<UUID> invitations;
    private org.bukkit.scoreboard.Team.OptionStatus collidable;
    private org.bukkit.scoreboard.Team.OptionStatus nameTagVisibility;
    private boolean isFriendlyFireAllowed;

    @Nullable
    private final UUID leaderUniqueId;
    private String color;

    public Team(String id, String randomColor) {
        this.color = randomColor;
        this.id = id;
        this.name = String.format("[%s]", id);
        this.uniqueId = UUID.randomUUID();
        this.leaderUniqueId = null;
        this.invitations = new ArrayList<>();
        this.members = new ArrayList<>();
        this.collidable = org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
        this.nameTagVisibility = org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
    }


    public Team(String id, @Nullable UUID leaderUniqueId, String randomColor) {
        this.color = randomColor;
        this.id = id;
        this.name = String.format("[%s]", id);
        this.uniqueId = UUID.randomUUID();
        this.leaderUniqueId = leaderUniqueId;
        this.invitations = new ArrayList<>();
        this.members = new ArrayList<>();
        this.collidable = org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
        this.nameTagVisibility = org.bukkit.scoreboard.Team.OptionStatus.ALWAYS;
    }

    // Constructor for config.
    public Team(
            String id,
            String name,
            String color,
            UUID uniqueId,
            @Nullable UUID leaderUniqueId,
            ArrayList<ITeamMember> members,
            ArrayList<UUID> invitations,
            org.bukkit.scoreboard.Team.OptionStatus collidable,
            org.bukkit.scoreboard.Team.OptionStatus nameTagVisibility,
            boolean isFriendlyFireAllowed
    ) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.uniqueId = uniqueId;
        this.leaderUniqueId = leaderUniqueId;
        this.invitations = invitations;
        this.members = members;
        this.collidable = collidable;
        this.nameTagVisibility = nameTagVisibility;
        this.isFriendlyFireAllowed = isFriendlyFireAllowed;
    }

    @Override
    public List<ITeamMember> getMembers() {
        return members;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setCollidable(org.bukkit.scoreboard.Team.OptionStatus status) {
        this.collidable = status;
    }

    @Override
    public org.bukkit.scoreboard.Team.OptionStatus getCollidable() {
        return collidable;
    }

    @Override
    public void setNameTagVisibility(org.bukkit.scoreboard.Team.OptionStatus status) {
        this.nameTagVisibility = status;
    }

    @Override
    public org.bukkit.scoreboard.Team.OptionStatus getNameTagVisibility() {
        return this.nameTagVisibility;
    }

    @Override
    public void setFriendlyFireAllowed(boolean value) {
        this.isFriendlyFireAllowed = value;
    }

    @Override
    public boolean isFriendlyFireAllowed() {
        return isFriendlyFireAllowed;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public @Nullable UUID getLeaderUniqueId() {
        return leaderUniqueId;
    }

    @Override
    public boolean containsMember(UUID uniqueId) {
        return this.members.stream().anyMatch(member -> member.getUniqueId().toString().equalsIgnoreCase(uniqueId.toString()));
    }

    @Override
    public ITeamMember getMember(UUID uniqueId) {
        return this.members.stream().filter(member -> member.getUniqueId().toString().equalsIgnoreCase(uniqueId.toString())).findFirst().orElse(null);
    }

    @Override
    public void removeMember(UUID uniqueId) {
        this.members.removeIf(member -> member.getUniqueId().toString().equalsIgnoreCase(uniqueId.toString()));
    }

    @Override
    public int getNameAsNumber() throws NumberFormatException {
        return Integer.parseInt(id);
    }

    @Override
    public ArrayList<UUID> getInvitations() {
        return invitations;
    }

    @Override
    public String getColor() {
        return color;
    }

    @Override
    public void setColor(String color) {
        this.color = color;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }


    @Override
    public void save(FileConfiguration teamConfig) {
        ConfigurationSection section = teamConfig.getConfigurationSection(this.uniqueId.toString());
        if (section == null)
            section = teamConfig.createSection(this.uniqueId.toString());
        section.set("uniqueId", this.uniqueId.toString());
        section.set("leaderUniqueId", this.leaderUniqueId == null ? null : this.leaderUniqueId.toString());
        List<Map<String, String>> members = this.members.stream().map(member -> {
            Map<String, String> arr = new HashMap<>();
            arr.put(member.getUniqueId().toString(), member.getName());
            return arr;
        }).toList();
        section.set("members", members);
        section.set("invitations", this.invitations.stream().map(UUID::toString).toList());
        section.set("name", this.name);
        section.set("id", Objects.requireNonNullElse(this.id, this.name));
        section.set("collidable", this.collidable.name());
        section.set("name_tag_visibility", this.nameTagVisibility.name());
        section.set("is_friendly_fire_allowed", this.isFriendlyFireAllowed);
        section.set("color", this.color);
    }
}
