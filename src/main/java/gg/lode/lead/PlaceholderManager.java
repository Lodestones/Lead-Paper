package gg.lode.lead;

import gg.lode.bookshelfapi.api.util.EnumHelper;
import gg.lode.leadapi.api.GeneratorType;
import gg.lode.leadapi.api.ITeam;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

        GeneratorType generatorType = EnumHelper.fetchEnum(GeneratorType.class, plugin.random().getString("type"), GeneratorType.NUMBER);
        ITeam team = plugin.getTeam(player.getUniqueId());

        return switch (params) {
            case "team_id" -> team == null ? generatorType == GeneratorType.NUMBER ? String.valueOf(Integer.MAX_VALUE) : null : team.getId();
            case "team_name" -> team == null ? null : team.getName();
            case "team_size" -> team == null ? null : String.valueOf(team.getMembers().size());
            case "team_color" -> team == null ? null : team.getColor();
            default -> null;
        };
    }
}
