package to.lodestone.lead.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import to.lodestone.lead.LeadPaper;

public class WorldListener implements Listener {

    private final LeadPaper plugin;

    public WorldListener(LeadPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void on(WorldSaveEvent event) {
        plugin.save();
    }

}
