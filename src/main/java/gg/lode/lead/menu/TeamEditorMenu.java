package gg.lode.lead.menu;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.item.ItemBuilder;
import gg.lode.bookshelfapi.api.menu.Menu;
import gg.lode.bookshelfapi.api.menu.build.MenuBuilder;
import gg.lode.bookshelfapi.api.menu.build.TopMenuBuilder;
import gg.lode.bookshelfapi.api.util.MiniMessageUtil;
import gg.lode.lead.LeadPlugin;
import gg.lode.leadapi.api.ITeam;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TeamEditorMenu extends Menu {
    private final ITeam team;
    private final LeadPlugin plugin;
    public TeamEditorMenu(LeadPlugin plugin, Player player, ITeam team) {
        super(player);
        this.plugin = plugin;
        this.team = team;
    }

    @Override
    protected @NotNull TopMenuBuilder getTopMenuBuilder(TopMenuBuilder builder) {
        return builder
                .setRows(3)
                .outline(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).title("").build())
                .setTitle("Editing: Team %s", team.getName())
                .editRow(1, rowBuilder -> {
                    rowBuilder
                            .setSlot(1, new ItemBuilder(Material.PAPER).title("Id").lore(String.format("<gray>Currently: <white>%s", team.getId())).build(), event -> {
                                event.setCancelled(true);
                                SignGUI.builder()
                                        .setLines("", "^^^^^^^^^^^^", "Enter a new", "team id")
                                        .setType(Material.OAK_SIGN)
                                        .setColor(DyeColor.BLACK)
                                        .setHandler((p, result) -> {
                                            String input = result.getLineWithoutColor(0);

                                            if (input.isEmpty()) {
                                                // The user has not entered anything on line 2, so we open the sign again
                                                return List.of(SignGUIAction.displayNewLines("", "^^^^^^^^^^^^", "Enter a new", "team id"));
                                            }

                                            if (input.matches("[a-zA-Z0-9_]+")) {
                                                team.setId(input);
                                                if (plugin.config().getBoolean("automatic_updates", true))
                                                    plugin.update();
                                                player.sendMessage(MiniMessageUtil.deserialize("<green>Successfully set the team id to %s.", team.getId()));
                                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                                                Task.later(plugin, this::update, 1);
                                            } else {
                                                player.sendMessage(MiniMessageUtil.deserialize("<red>Please input a valid team id."));
                                            }

                                            return Collections.emptyList();
                                        })
                                        .build()
                                        .open(player);
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                            })
                            .setSlot(2, new ItemBuilder(Material.NAME_TAG).title("Display Name").lore(String.format("<gray>Currently: <white>%s", team.getName())).build(), event -> {
                                event.setCancelled(true);
                                SignGUI.builder()
                                        .setLines("", "^^^^^^^^^^^^", "Enter a new", "display name")
                                        .setType(Material.OAK_SIGN)
                                        .setColor(DyeColor.BLACK)
                                        .setHandler((p, result) -> {
                                            String input = result.getLineWithoutColor(0);

                                            if (input.isEmpty()) {
                                                // The user has not entered anything on line 2, so we open the sign again
                                                return List.of(SignGUIAction.displayNewLines("", "^^^^^^^^^^^^", "Enter a new", "display name"));
                                            }

                                            team.setName(input);
                                            if (plugin.config().getBoolean("automatic_updates", true))
                                                plugin.update();
                                            player.sendMessage(MiniMessageUtil.deserialize("<green>Successfully set the team name to %s.", team.getName()));
                                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                                            Task.later(plugin, () -> new TeamEditorMenu(plugin, player, team).open(), 1);
                                            return Collections.emptyList();
                                        })
                                        .build()
                                        .open(player);
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                            })
                            .setSlot(3, new ItemBuilder(Material.GLASS).title("Collidable").lore(String.format("<gray>Currently: <white>%s", team.getCollidable().name())).build(), event -> {
                                event.setCancelled(true);
                                int ordinal = team.getCollidable().ordinal();
                                team.setCollidable(Team.OptionStatus.values()[(ordinal + 1) % Team.OptionStatus.values().length]);
                                this.update();
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();
                            })
                            .setSlot(4, new ItemBuilder(Material.POTION).flags(ItemFlag.HIDE_ATTRIBUTES).addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1, 1)).potionColor(Color.WHITE).title("Name Tag Visibility").lore(String.format("<gray>Currently: <white>%s", team.getNameTagVisibility().name())).build(), event -> {
                                event.setCancelled(true);
                                int ordinal = team.getNameTagVisibility().ordinal();
                                team.setNameTagVisibility(Team.OptionStatus.values()[(ordinal + 1) % Team.OptionStatus.values().length]);
                                this.update();
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();
                            })
                            .setSlot(5, new ItemBuilder(Material.GOLDEN_SWORD).title("Friendly Fire").lore(team.isFriendlyFireAllowed() ? "<green>Enabled" : "<red>Disabled").build(), event -> {
                                event.setCancelled(true);
                                team.setFriendlyFireAllowed(!team.isFriendlyFireAllowed());
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                this.update();
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();
                            })
                            .setSlot(6, new ItemBuilder(Material.WHITE_WOOL).title("Color").lore(String.format("<gray>Currently: <white>%s", team.getColor())).build(), event -> {
                                event.setCancelled(true);
                                SignGUI.builder()
                                        .setLines("", "^^^^^^^^^^^^", "Enter a valid", "hex color")
                                        .setType(Material.OAK_SIGN)
                                        .setColor(DyeColor.BLACK)
                                        .setHandler((p, result) -> {
                                            String input = result.getLineWithoutColor(0);

                                            if (input.isEmpty()) {
                                                // The user has not entered anything on line 2, so we open the sign again
                                                return List.of(SignGUIAction.displayNewLines("", "^^^^^^^^^^^^", "Enter a valid", "hex color"));
                                            }

                                            if (input.matches("^#?([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
                                                if (!input.startsWith("#")) input = "#" + input;

                                                team.setColor(input);
                                                if (plugin.config().getBoolean("automatic_updates", true))
                                                    plugin.update();
                                                player.sendMessage(MiniMessageUtil.deserialize("<green>Successfully set the team color to <%s>%s.", team.getColor(), team.getColor()));
                                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                                                Task.later(plugin, this::update, 1);
                                            } else {
                                                player.sendMessage(MiniMessageUtil.deserialize("<red>Please input a valid hex color."));
                                            }

                                            return Collections.emptyList();
                                        })
                                        .build()
                                        .open(player);
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                            })
                            .setSlot(7, new ItemBuilder(Material.BEDROCK).title("Coming Soon").lore("<gray>Reserved for future updates.").build(), event -> {
                                event.setCancelled(true);
                            });
                });
    }

    @Override
    protected @Nullable MenuBuilder getBottomMenuBuilder(MenuBuilder menuBuilder) {
        return null;
    }
}
