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
            setupSettings();
            setupTraceOverride(); // Первый запуск

            ui.hudGroup.fill(t -> {
                t.name = "sam-hud-button";
                t.right();
                // Создаем кнопку и сохраняем ссылку на её ячейку (Cell)
                var cell = t.button(Icon.admin, () -> {}).size(50f);
                var btn = cell.get();

                final float[] holdTimer = {0f};
                final boolean[] longPressedTriggered = {false};
                final int[] lastY = {-1};
                btn.update(() -> {
                    int currentY = Core.settings.getInt("sam-hud-y", 60);
                    if (lastY[0] != currentY) {
                        cell.padTop(currentY);
                        lastY[0] = currentY;
                        t.invalidateHierarchy();
                    }

                    // 2. Логика зажатия
                    if(btn.isPressed()){
                        holdTimer[0] += Core.graphics.getDeltaTime() * 60f; // Переводим в тики (60 тиков = 1 сек)

                        if(holdTimer[0] > 60f && !longPressedTriggered[0]){
                            Call.sendChatMessage("/history");
                            longPressedTriggered[0] = true;
                        }
                    } else {
                        holdTimer[0] = 0f;
                    }
                });

                btn.clicked(() -> {
                    if(!longPressedTriggered[0]){
                        adminList.toggle();
                    }
                    longPressedTriggered[0] = false;
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
            lastAutoTime.clear();

            setupTraceOverride();

            Timer.schedule(() -> {
                if(net.client() && player.unit() != null && Core.settings.getBool("sam-vanish", false)){
                    Call.sendChatMessage("/vanish 1");
                    Log.info("[#00ff]Vanish on");
                }
            }, 7f);

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

    private void setupSettings() {
        ui.settings.addCategory("SimpleAdmin", Icon.admin, table -> {
            table.row();
            table.check("Отслеживать статистику", Core.settings.getBool("sam-show-stats", false), val -> {
                Core.settings.put("sam-show-stats", val);
            }).left().row();

            table.check("Активировать скрытность?", Core.settings.getBool("sam-vanish", false), val -> {
                Core.settings.put("sam-vanish", val);
            }).left().row();

            table.labelWrap("Размер кнопок").left().row();
            table.slider(30, 60, 1, Core.settings.getInt("sam-btn-size", 40), val -> {
                Core.settings.put("sam-btn-size", (int)val);
            }).row();

            table.labelWrap("Смещение кнопки HUD (Y)").left().row();
            table.slider( 0, 600, 5, Core.settings.getInt("sam-hud-y", 60), val -> {
                Core.settings.put("sam-hud-y", (int)val);
            }).row();


            table.button("Сбросить настройки", () -> {
                Core.settings.put("sam-show-stats", false);
                Core.settings.put("sam-btn-size", 40);
                Core.settings.put("sam-hud-y", 60);
                ui.showInfoFade("Настройки сброшены");
            }).margin(10).width(240f).padTop(20f);
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
            data.name = p.name;
            data.player = p;
        }
    }

    public class CustomTraceDialog extends TraceDialog {
        @Override
        public void show(Player player, Administration.TraceInfo info) {
            if (!handleTraceLogic(player, info)) {
                originalTraces.show(player, info);
            }
        }

        public void show(Player player, Administration.TraceInfo info, boolean offline) {//фикс дял клиента
            if (!handleTraceLogic(player, info)) {
                try {
                    var method = originalTraces.getClass().getMethod("show", Player.class, Administration.TraceInfo.class, boolean.class);
                    method.invoke(originalTraces, player, info, offline);
                } catch (Exception e) {
                    originalTraces.show(player, info);
                }
            }
        }

        private boolean handleTraceLogic(Player player, Administration.TraceInfo info) {
            PlayerData data = playerHistory.get(player.id);
            if (data != null && info.uuid != null && !info.uuid.isEmpty()) {
                data.updateFrom(info);
            }

            boolean wasAuto = autoTraceRequested.remove(player.id);
            if (wasAuto) {
                lastAutoTime.put(player.id, Time.time);
                Log.info("📋 [accent]" + player.name + "[white]: " + (data != null && data.uuid.equals("admin?") ? "админ (локально)" : "данные получены"));
                return true;
            }
            float lastTime = lastAutoTime.get(player.id, 0f);
            if (Time.time - lastTime < 1.1f) {
                return true;
            }
            return false;
        }
    }
}