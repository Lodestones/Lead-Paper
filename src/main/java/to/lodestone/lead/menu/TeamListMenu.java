package to.lodestone.lead.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import to.lodestone.bookshelfapi.api.menu.Menu;
import to.lodestone.bookshelfapi.api.menu.build.MenuBuilder;
import to.lodestone.bookshelfapi.api.menu.build.TopMenuBuilder;
import to.lodestone.bookshelfapi.api.util.ArrayUtil;
import to.lodestone.bookshelfapi.api.util.MiniMessageUtil;
import to.lodestone.lead.LeadPlugin;
import to.lodestone.leadapi.api.ITeam;
import to.lodestone.leadapi.api.ITeamMember;

import java.util.ArrayList;
import java.util.List;

public class TeamListMenu extends Menu {

    private final int page;
    private final LeadPlugin plugin;

    public TeamListMenu(LeadPlugin plugin, Player player, int page) {
        super(player);
        this.plugin = plugin;
        this.page = page;
    }

    @Override
    protected @NotNull TopMenuBuilder getTopMenuBuilder(TopMenuBuilder topMenuBuilder) {
        List<ITeam> teams = plugin.getTeams();
//        teams.sort(Comparator.comparingInt(ITeam::getNameAsNumber));

        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.empty()));

        topMenuBuilder
                .setTitle("Team List")
                .setRows(6)
                .outline(pane);

        List<List<ITeam>> chunkedPages = ArrayUtil.chunk(teams, 7 * 4);
        List<ITeam> currentPage = chunkedPages.get(page);

        ItemStack goBack = new ItemStack(Material.ARROW);
        goBack.editMeta(ItemMeta.class, meta -> meta.displayName(MiniMessageUtil.deserialize("<green>Go Back").decoration(TextDecoration.ITALIC, false)));

        ItemStack goForward = new ItemStack(Material.ARROW);
        goForward.editMeta(ItemMeta.class, meta -> meta.displayName(MiniMessageUtil.deserialize("<green>Go Forward").decoration(TextDecoration.ITALIC, false)));

        ItemStack back = new ItemStack(Material.BARRIER);
        back.editMeta(ItemMeta.class, meta -> meta.displayName(MiniMessageUtil.deserialize("<reset><red>Back").decoration(TextDecoration.ITALIC, false)));

        topMenuBuilder.editRow(0,
                rowBuilder -> rowBuilder.setSlot(0, back,
                        event -> {
                            event.setCancelled(true);
                            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                        })
        );

        int[] _c = {1, 1};
        for (ITeam team : currentPage) {
            if (_c[0] > 7) {
                _c[0] = 1;
                _c[1]++;
                if (_c[1] == 5) break;
            }

            ItemStack listHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) listHead.getItemMeta();
            meta.displayName(MiniMessageUtil.deserialize(String.format("<reset><%s>%s", team.getColor(), team.getName())).decoration(TextDecoration.ITALIC, false));
            List<Component> lores = new ArrayList<>();
            List<ITeamMember> members = new ArrayList<>(team.getMembers());
            members.sort((a, b) -> {
                if (a.getUniqueId().equals(team.getLeaderUniqueId())) return 1;
                else if (b.getUniqueId().equals(team.getLeaderUniqueId())) return -1;
                else return 0;
            });

            for (ITeamMember teamMember : members)
                lores.add(MiniMessageUtil.deserialize(String.format("<reset><white>- <yellow>%s", teamMember.getName())).decoration(TextDecoration.ITALIC, false));
            meta.lore(lores);
            if (team.getLeaderUniqueId() != null && team.containsMember(team.getLeaderUniqueId())) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(team.getLeaderUniqueId());
                if (offlinePlayer.getName() != null)
                    meta.setOwningPlayer(offlinePlayer);
            }

            listHead.setItemMeta(meta);

            topMenuBuilder.editRow(_c[1], rowBuilder -> rowBuilder.setSlot(_c[0], listHead, event -> event.setCancelled(true)));

            _c[0]++;
        }

        return topMenuBuilder
                .editRow(5, rowBuilder -> {
                    if (page > 0) {
                        rowBuilder.setSlot(0,
                                goBack,
                                event -> {
                                    event.setCancelled(true);
                                    new TeamListMenu(plugin, player, page - 1).open();
                                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                }
                        );
                    }

                    if (page < chunkedPages.size() - 1) {
                        rowBuilder.setSlot(8,
                                goForward,
                                event -> {
                                    event.setCancelled(true);
                                    new TeamListMenu(plugin, player, page + 1).open();
                                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 2);
                                }
                        );
                    }
                });
    }

    @Override
    protected @Nullable MenuBuilder getBottomMenuBuilder(MenuBuilder menuBuilder) {
        return null;
    }
}
