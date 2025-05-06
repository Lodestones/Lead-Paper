package gg.lode.lead.team;

import gg.lode.leadapi.api.ITeamMember;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TeamMember implements ITeamMember {

    private final UUID uniqueId;
    private final String name;
    private boolean isInTeamChat;

    public TeamMember(Player player) {
        this.uniqueId = player.getUniqueId();
        this.name = player.getName();
        this.isInTeamChat = false;
    }

    public TeamMember(UUID uniqueId, String name) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.isInTeamChat = false;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getName() {
        return name;
    }

    public boolean isInTeamChat() {
        return isInTeamChat;
    }

    public void setInTeamChat(boolean status) {
        isInTeamChat = !isInTeamChat;
    }

}
