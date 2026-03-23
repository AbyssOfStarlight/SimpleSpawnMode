package fallen;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.Mod;
import mindustry.net.Administration;
import mindustry.ui.dialogs.TraceDialog;

import static mindustry.Vars.*;

public class SimpleAdminMode extends Mod {
    private SimpleAdminList adminList = new SimpleAdminList();
    public static ObjectMap<Integer, PlayerData> playerHistory = new ObjectMap<>();
    private ObjectSet<Integer> autoTraceRequested = new ObjectSet<>();
    private IntSet knownPlayerIds = new IntSet();
    private static TraceDialog originalTraces;


    public SimpleAdminMode() {
        Events.on(ClientLoadEvent.class, e -> {
            PlayerStatsTracker.init();

            adminList.build(Core.scene.root);
            setupTraceOverride(); // Первый запуск

            ui.hudGroup.fill(t -> {
                t.right().margin(10).marginTop(60);
                t.button(Icon.admin, () -> adminList.toggle()).size(50);
            });
        });

//        Events.on(PlayerJoin.class, e -> {
//            Time.run(60f, () -> processPlayer(e.player));
//            Vars.player.sendMessage("PlayerJoin");
//
//        });//
//        Events.on(PlayerLeave.class, e -> {
//            PlayerData data = playerHistory.get(e.player.id);
//            if (data != null) data.online = false;
//            Vars.player.sendMessage("PlayerLeave");
//        });

        Events.on(WorldLoadEvent.class, e -> {
            playerHistory.clear();
            autoTraceRequested.clear();
            knownPlayerIds.clear();

            setupTraceOverride();

            if(Vars.state.isMenu() || !Vars.net.active()) return;
            Time.run(120f, () -> {
                for (Player p : Groups.player) processPlayer(p);
            });
        });

        Events.run(Trigger.update, () -> {
            if(Vars.state.isMenu() || !Vars.net.active()) return;
            if (net.active() && state.isGame() && Core.graphics.getFrameId() % 60 == 0) {
                syncPlayers();
            }
        });
    }

    private void setupTraceOverride() {
        if (originalTraces == null) {
            originalTraces = ui.traces;
        }

        // Если ui.traces сбросился
        if (!(ui.traces instanceof CustomTraceDialog)) {
            ui.traces = new CustomTraceDialog();
            PlayerData.setAutoTraceRequested(autoTraceRequested);
            Log.info("SimpleAdminMode: TraceDialog перехвачен");
        }
    }

    private void syncPlayers() {
        IntSet currentIds = new IntSet();
        for (Player p : Groups.player) {
            currentIds.add(p.id);
            processPlayer(p);
        }

        // Новые игроки
        currentIds.each(id -> {
            if (!knownPlayerIds.contains(id)) {
                Log.info("SimpleAdminMode: Новый игрок ID=@ (name=@)", id,
                        Groups.player.getByID(id) != null ? Groups.player.getByID(id).name : "?");
                ui.showInfoFade("[green]+ [white]" +
                        (Groups.player.getByID(id) != null ? Groups.player.getByID(id).name : "???"));
            }
        });

        // Вышедшие игроки
        knownPlayerIds.each(id -> {
            if (!currentIds.contains(id)) {
                Log.info("SimpleAdminMode: Игрок вышел ID=@ ", id);
                PlayerData data = playerHistory.get(id);
                if (data != null) {
                    data.online = false;
                    data.stopTraceRequests();
                    ui.showInfoFade("[red]- [white]" + data.name);
                }
            }
        });

        knownPlayerIds = currentIds;
    }

    private void processPlayer(Player p) {
        PlayerData data = playerHistory.get(p.id);

        if (data == null) {
            data = new PlayerData(p);
            playerHistory.put(p.id, data);
            // Запускаем периодические запросы
            data.startTraceRequests(p);
        } else {
            if (!data.online) {
                data.reset();
                data.startTraceRequests(p); // Перезапускаем
            }
            data.name = p.name;
            data.online = true;
            data.player = p;
        }
    }

    public class CustomTraceDialog extends TraceDialog {
        @Override
        public void show(Player player, Administration.TraceInfo info) {
            boolean isAutoRequest = autoTraceRequested.remove(player.id);

            PlayerData data = playerHistory.get(player.id);
            if (data != null && info.uuid != null && !info.uuid.isEmpty()) {
                data.setUuid(info.uuid);
                Log.info("SimpleAdminMode: UUID для @ = @", data.name, info.uuid);
            }

            if (isAutoRequest) {
                Log.info("📋 [accent]" + player.name + "[white]: " + (data != null && data.uuid.equals("admin?") ? "админ (локально)" : "данные получены"));
            } else {
                originalTraces.show(player, info);
            }
        }
    }
}