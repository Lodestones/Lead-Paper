package to.lodestone.lead;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import to.lodestone.leadapi.api.ITeam;

public class PlaceholderManager extends PlaceholderExpansion {

    private final LeadPlugin plugin;
    public PlaceholderManager(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lead";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Lodestone";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return null;

        ITeam team = plugin.getTeam(player.getUniqueId());
        if (team == null) return null;

        return switch (params) {
            case "team_id" -> team.getId();
            case "team_name" -> team.getName();
            case "team_size" -> String.valueOf(team.getMembers().size());
            case "team_color" -> team.getColor();
            default -> null;
        };

    }
}
