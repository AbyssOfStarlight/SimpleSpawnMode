package fallen;

import arc.*;
import mindustry.ui.dialogs.BaseDialog;

public class SimpleAdminSettings extends BaseDialog {

    public SimpleAdminSettings() {
        super("Настройки SimpleAdmin");
        addCloseButton();

        setup();
    }

    private void setup() {
        cont.clear();
        cont.defaults().pad(10).fillX();

        // Настройка размера кнопок
        cont.table(t -> {
            t.add("Размер кнопок: ").left();
            t.add(new arc.scene.ui.Label(() -> String.valueOf(Core.settings.getInt("sam-btn-size", 40)))).width(40);
            t.slider(30, 60, 1, Core.settings.getInt("sam-btn-size", 40), val -> {
                Core.settings.put("sam-btn-size", (int)val);
            }).growX();
        }).row();

        // Настройка высоты кнопки на HUD
        cont.table(t -> {
            t.add("Сдвиг кнопки (Y): ").left();
            t.row();
            t.add(new arc.scene.ui.Label(() -> String.valueOf(Core.settings.getInt("sam-hud-y", 60)))).width(40);
            t.slider(-600, 600, 10, Core.settings.getInt("sam-hud-y", 60), val -> {
                Core.settings.put("sam-hud-y", (int)val);
            }).growX();
        }).row();

        // Настройка ширины таблицы
        cont.table(t -> {
            t.add("Настройка ширины таблицы: ").left();
            t.add(new arc.scene.ui.Label(() -> String.valueOf(Core.settings.getInt("sam-list-w", 400)))).width(40);
            t.slider(200, 800, 10, Core.settings.getInt("sam-list-w", 400), val -> {
                Core.settings.put("sam-list-w", (int)val);
            }).growX();
        }).row();

        cont.check("Отслеживать статистику", Core.settings.getBool("sam-show-stats", false), val -> {
            Core.settings.put("sam-show-stats", val);
        }).left();
    }
}