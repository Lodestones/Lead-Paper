package gg.lode.lead.menu;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import de.rapha149.signgui.exception.SignGUIVersionException;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.item.ItemBuilder;
import gg.lode.bookshelfapi.api.menu.Menu;
import gg.lode.bookshelfapi.api.menu.build.MenuBuilder;
import gg.lode.bookshelfapi.api.menu.build.TopMenuBuilder;
import gg.lode.bookshelfapi.api.util.VariableContext;
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
                .setTitle(VariableContext.of("name", team.getName()).replace(plugin.text(player, "lead.menu.title.team_editor")))
                .editRow(1, rowBuilder -> {
                    rowBuilder
                            .setSlot(1, new ItemBuilder(Material.PAPER).title(plugin.text(player, "lead.menu.item.id")).lore(VariableContext.of("value", team.getId()).replace(plugin.text(player, "lead.menu.item.currently"))).build(), event -> {
                                event.setCancelled(true);
                                try {
                                    SignGUI.builder()
                                            .setLines("", "^^^^^^^^^^^^", plugin.text(player, "lead.menu.sign.enter_new"), plugin.text(player, "lead.menu.sign.team_id"))
                                            .setType(Material.OAK_SIGN)
                                            .setColor(DyeColor.BLACK)
                                            .setHandler((p, result) -> {
                                                String input = result.getLineWithoutColor(0);

                                                if (input.isEmpty()) {
                                                    // The user has not entered anything on line 2, so we open the sign again
                                                    return List.of(SignGUIAction.displayNewLines("", "^^^^^^^^^^^^", plugin.text(player, "lead.menu.sign.enter_new"), plugin.text(player, "lead.menu.sign.team_id")));
                                                }

                                                if (input.matches("[a-zA-Z0-9_]+")) {
                                                    team.setId(input);
                                                    if (plugin.config().getBoolean("automatic_updates", true))
                                                        plugin.update();
                                                    player.sendMessage(plugin.message(player, "lead.menu.id.success", VariableContext.of("id", team.getId())));
                                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                                                    Task.later(plugin, this::update, 1);
                                                } else {
                                                    player.sendMessage(plugin.message(player, "lead.menu.id.invalid"));
                                                }

                                                return Collections.emptyList();
                                            })
                                            .build()
                                            .open(player);
                                } catch (SignGUIVersionException e) {
                                    throw new RuntimeException(e);
                                }
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                            })
                            .setSlot(2, new ItemBuilder(Material.NAME_TAG).title(plugin.text(player, "lead.menu.item.display_name")).lore(VariableContext.of("value", team.getName()).replace(plugin.text(player, "lead.menu.item.currently"))).build(), event -> {
                                event.setCancelled(true);
                                try {
                                    SignGUI.builder()
                                            .setLines("", "^^^^^^^^^^^^", plugin.text(player, "lead.menu.sign.enter_new"), plugin.text(player, "lead.menu.sign.display_name"))
                                            .setType(Material.OAK_SIGN)
                                            .setColor(DyeColor.BLACK)
                                            .setHandler((p, result) -> {
                                                String input = result.getLineWithoutColor(0);

                                                if (input.isEmpty()) {
                                                    // The user has not entered anything on line 2, so we open the sign again
                                                    return List.of(SignGUIAction.displayNewLines("", "^^^^^^^^^^^^", plugin.text(player, "lead.menu.sign.enter_new"), plugin.text(player, "lead.menu.sign.display_name")));
                                                }

                                                team.setName(input);
                                                if (plugin.config().getBoolean("automatic_updates", true))
                                                    plugin.update();
                                                player.sendMessage(plugin.message(player, "lead.menu.name.success", VariableContext.of("name", team.getName())));
                                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                                                Task.later(plugin, () -> new TeamEditorMenu(plugin, player, team).open(), 1);
                                                return Collections.emptyList();
                                            })
                                            .build()
                                            .open(player);
                                } catch (SignGUIVersionException e) {
                                    throw new RuntimeException(e);
                                }
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                            })
                            .setSlot(3, new ItemBuilder(Material.GLASS).title(plugin.text(player, "lead.menu.item.collidable")).lore(VariableContext.of("value", team.getCollidable().name()).replace(plugin.text(player, "lead.menu.item.currently"))).build(), event -> {
                                event.setCancelled(true);
                                int ordinal = team.getCollidable().ordinal();
                                team.setCollidable(Team.OptionStatus.values()[(ordinal + 1) % Team.OptionStatus.values().length]);
                                this.update();
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();
                            })
                            .setSlot(4, new ItemBuilder(Material.POTION).flags(ItemFlag.HIDE_ATTRIBUTES).addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1, 1)).potionColor(Color.WHITE).title(plugin.text(player, "lead.menu.item.name_tag")).lore(VariableContext.of("value", team.getNameTagVisibility().name()).replace(plugin.text(player, "lead.menu.item.currently"))).build(), event -> {
                                event.setCancelled(true);
                                int ordinal = team.getNameTagVisibility().ordinal();
                                team.setNameTagVisibility(Team.OptionStatus.values()[(ordinal + 1) % Team.OptionStatus.values().length]);
                                this.update();
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();
                            })
                            .setSlot(5, new ItemBuilder(Material.GOLDEN_SWORD).title(plugin.text(player, "lead.menu.item.friendly_fire")).lore(plugin.text(player, team.isFriendlyFireAllowed() ? "lead.menu.state.enabled" : "lead.menu.state.disabled")).build(), event -> {
                                event.setCancelled(true);
                                team.setFriendlyFireAllowed(!team.isFriendlyFireAllowed());
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                this.update();
                                if (plugin.config().getBoolean("automatic_updates", true))
                                    plugin.update();
                            })
                            .setSlot(6, new ItemBuilder(Material.WHITE_WOOL).title(plugin.text(player, "lead.menu.item.color")).lore(VariableContext.of("value", team.getColor()).replace(plugin.text(player, "lead.menu.item.currently"))).build(), event -> {
                                event.setCancelled(true);
                                try {
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
                                                    player.sendMessage(plugin.message(player, "lead.menu.color.success", VariableContext.of("color", team.getColor()).with("color", team.getColor())));
                                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
                                                    Task.later(plugin, this::update, 1);
                                                } else {
                                                    player.sendMessage(plugin.message(player, "lead.menu.color.invalid"));
                                                }

                                                return Collections.emptyList();
                                            })
                                            .build()
                                            .open(player);
                                } catch (SignGUIVersionException e) {
                                    throw new RuntimeException(e);
                                }
                                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                            })
                            .setSlot(7, new ItemBuilder(Material.BEDROCK).title(plugin.text(player, "lead.menu.item.coming_soon")).lore(plugin.text(player, "lead.menu.item.coming_soon_lore")).build(), event -> {
                                event.setCancelled(true);
                            });
                });
    }

    @Override
    protected @Nullable MenuBuilder getBottomMenuBuilder(MenuBuilder menuBuilder) {
        return null;
    }
}
