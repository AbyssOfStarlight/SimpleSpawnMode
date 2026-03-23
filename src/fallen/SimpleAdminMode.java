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
    private static ObjectMap<Integer, Float> lastAutoTime = new ObjectMap<>();


    public SimpleAdminMode() {
        Events.on(ClientLoadEvent.class, e -> {
            PlayerStatsTracker.init();

            adminList.build(Core.scene.root);
            setupTraceOverride(); // Первый запуск

            ui.hudGroup.fill(t -> {
                t.name = "sam-hud-button";
                t.right();
                // Создаем кнопку и сохраняем ссылку на её ячейку (Cell)
                var cell = t.button(Icon.admin, () -> adminList.toggle()).size(50f);

                final int[] lastY = {-1};

                t.update(() -> {
                    int currentY = Core.settings.getInt("sam-hud-y", 60);

                    if (lastY[0] != currentY) {
                        cell.padTop(currentY);
                        lastY[0] = currentY;
                        t.invalidateHierarchy(); // Заставляем таблицу пересчитать положение
                    }
                });
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

            boolean wasAuto = autoTraceRequested.remove(player.id);
            if (wasAuto) {
                lastAutoTime.put(player.id, Time.time);
                Log.info("📋 [accent]" + player.name + "[white]: " + (data != null && data.uuid.equals("admin?") ? "админ (локально)" : "данные получены"));
            }
            float lastTime = lastAutoTime.get(player.id, 0f);
            if (Time.time - lastTime < 1f) {
                return;
            }
            originalTraces.show(player, info);
        }
    }
}