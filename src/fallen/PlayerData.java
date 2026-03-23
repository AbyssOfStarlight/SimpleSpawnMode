package fallen;

import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Time;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Packets;

public class PlayerData {
    public int id;
    public String name;
    public String uuid;
    public boolean online;
    public Player player;
    public int builds = 0;
    public int breaks = 0;
    public int configs = 0;

    // Для запросов
    public int traceAttempts = 0;
    public float lastTraceRequest = 0f;
    public arc.util.Timer.Task traceTask;
    private static ObjectSet<Integer> autoTraceRequested;

    public static final int MAX_TRACE_ATTEMPTS = 3;
    public static final float TRACE_INTERVAL = 3f;

    public PlayerData(Player p) {
        this.id = p.id;
        this.name = p.name;
        this.uuid = "Загрузка...";
        this.online = true;
        this.player = p;
    }


    public static void setAutoTraceRequested(ObjectSet<Integer> set) {
        autoTraceRequested = set;
    }

    // Периодические запросы
    public void startTraceRequests(Player p) {
        if (traceTask != null) return; // Уже запущено

        traceTask = new arc.util.Timer.Task() {
            @Override
            public void run() {
                // Если UUID внезапно пришел между тиками таймера — стопаем
                if (uuid != null && !uuid.equals("Загрузка...") && !uuid.equals("admin?") && !uuid.equals("недоступен")) {
                    cancel();
                    traceTask = null;
                    return;
                }
                traceAttempts++;
                lastTraceRequest = Time.time;

                Log.info("SimpleAdminMode: Попытка #/@ для @ (uuid='@')",
                        traceAttempts, MAX_TRACE_ATTEMPTS, name, uuid);

                if (p != null && p.unit() != null && autoTraceRequested != null) {
                    autoTraceRequested.add(p.id);
                    Call.adminRequest(p, Packets.AdminAction.trace, null);
                }

                // Если попытки кончились — останавливаем
                if (traceAttempts >= MAX_TRACE_ATTEMPTS) {
                    cancel();
                    traceTask = null;

                    // Ставим заглушку
                    if (player != null && player.admin) {
                        uuid = "admin?";
                        Log.info("SimpleAdminMode: @ помечен как 'admin?'", name);
                    } else {
                        uuid = "недоступен";
                    }
                    return;
                }

                // Если UUID уже есть — останавливаем
                if (uuid != null && !uuid.isEmpty() &&
                        !uuid.equals("Загрузка...") && !uuid.equals("admin?") &&
                        !uuid.equals("недоступен")) {
                    cancel();
                    traceTask = null;
                    return;
                }
            }
        };

        Timer.schedule(traceTask, 0f, TRACE_INTERVAL);
    }

    public void stopTraceRequests() {
        if (traceTask != null) {
            traceTask.cancel();
            traceTask = null;
        }
    }

    public void reset() {
        stopTraceRequests();
        online = true;
        traceAttempts = 0;
        lastTraceRequest = 0f;
        if (!uuid.equals("admin?")) {
            uuid = "Загрузка...";
        }
        builds = 0;
        breaks = 0;
        configs = 0;
    }

    public void setUuid(String newUuid) {
        if (newUuid == null || newUuid.isEmpty()) return;
        this.uuid = newUuid;
        stopTraceRequests();
    }
}