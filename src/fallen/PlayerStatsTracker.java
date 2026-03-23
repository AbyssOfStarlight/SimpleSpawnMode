package fallen;

import arc.Events;
import mindustry.game.EventType.*;
import static fallen.SimpleAdminMode.playerHistory;
import arc.Core;

public class PlayerStatsTracker {
    public static void init() {

        Events.on(BlockBuildBeginEvent.class, e -> {
            if(!Core.settings.getBool("sam-show-stats", false)) return;
            if(e.unit == null) return;
            if(e.unit.getPlayer() == null) return;
            if(playerHistory == null) return;

            PlayerData data = playerHistory.get(e.unit.getPlayer().id);

            if(data == null) return;

            if(e.breaking) data.breaks++;
            else data.builds++;
        });

        Events.on(ConfigEvent.class, e -> {
            if(!Core.settings.getBool("sam-show-stats", false) || e.player == null) return;
            PlayerData data = playerHistory.get(e.player.id);
            if(data != null) data.configs++;
        });

        Events.on(BuildRotateEvent.class, e -> {
            if(!Core.settings.getBool("sam-show-stats", false) || e.unit.getPlayer() == null) return;
            PlayerData data = playerHistory.get(e.unit.getPlayer().id);
            if(data != null) data.configs++;
        });
    }
}