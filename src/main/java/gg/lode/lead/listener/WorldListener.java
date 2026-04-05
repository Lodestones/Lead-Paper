package gg.lode.lead.listener;

import gg.lode.bookshelfapi.api.Task;
import gg.lode.lead.LeadPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class WorldListener implements Listener {

    private final LeadPlugin plugin;
    private final AtomicBoolean saving = new AtomicBoolean(false);

    public WorldListener(LeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void on(WorldSaveEvent event) {
        // WorldSaveEvent fires for each world (overworld, nether, end).
        // Use CAS to ensure only one async save runs per cycle.
        if (saving.compareAndSet(false, true)) {
            Task.runAsync(plugin, () -> {
                try {
                    plugin.save();
                } finally {
                    saving.set(false);
                }
            });
        }
    }

}
