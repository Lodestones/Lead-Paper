package gg.lode.lead.listener;

import gg.lode.bookshelfapi.api.Task;
import gg.lode.lead.LeadPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;

public class WorldListener implements Listener {

    private final LeadPlugin plugin;

    public WorldListener(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void on(WorldSaveEvent event) {
        // Save asynchronously to avoid blocking the main thread during world saves
        Task.runAsync(plugin, plugin::save);
    }

}
