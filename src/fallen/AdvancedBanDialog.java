package fallen;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.*;
import mindustry.ui.dialogs.BaseDialog;

public class AdvancedBanDialog extends BaseDialog {
    private String currentScope = "here";

    public AdvancedBanDialog(Player player, String uuid) {
        super("Управление: " + Strings.stripColors(player.name));
        addCloseButton();

        cont.table(top -> {
            top.add("[lightgray]UUID: [accent]" + uuid).padRight(20);

            top.table(st -> {
                ButtonGroup<Button> sg = new ButtonGroup<>();
                st.button("HERE", Styles.togglet, () -> currentScope = "here").size(80, 40).group(sg).checked(true);
                st.button("ALL", Styles.togglet, () -> currentScope = "all").size(80, 40).group(sg);
                st.button("Attack", Styles.togglet, () -> currentScope = "attack").size(80, 40).group(sg);
                st.button("Survival", Styles.togglet, () -> currentScope = "survival").size(80, 40).group(sg);
                st.button("PvP", Styles.togglet, () -> currentScope = "pvp").size(80, 40).group(sg);

                st.button("E_ATK", Styles.togglet, () -> currentScope = "eattack").size(80, 40).group(sg);
                st.button("E_SRV", Styles.togglet, () -> currentScope = "esurvival").size(80, 40).group(sg);
            });
        }).row();

        cont.image().color(Pal.accent).fillX().height(3).pad(10).row();

        cont.pane(table -> {
            table.defaults().pad(4).fillX();

            addRuleRow(table, uuid, "2.1 NSFW", "2.1", 1, 30, 14, "d");

            addRuleRow(table, uuid, "2.2 Спам/флуд", "2.2", 1, 30, 3, "d");

            addRuleRow(table, uuid, "2.3 Оскорбления", "2.3", 3, 30, 3, "d");

            addRuleRow(table, uuid, "2.4 Политика", "2.4", 7, 14, 7, "d");

            addRuleRow(table, uuid, "2.7 Конфликты", "2.7", 7, 30, 7, "d");

            addRuleRow(table, uuid, "3.1.1 Трата ресурсов/юнитов", "3.1.1", 3, 30, 7, "d");

            addRuleRow(table, uuid, "3.1.2 Удаление построек", "3.1.2", 3, 60, 30, "d");

            addRuleRow(table, uuid, "3.1.3 Отключение энергоузлов", "3.1.3", 7, 30, 7, "d");

            addRuleRow(table, uuid, "3.1.4 Поломка вакумов", "3.1.4", 7, 30, 7, "d");

            addRuleRow(table, uuid, "3.1.5 Мусоросжигатели у ядра", "3.1.5", 0, 0, 0, "perm");

            addRuleRow(table, uuid, "3.1.6 Захламление пространства", "3.1.6", 1, 30, 1, "d");

            addRuleRow(table, uuid, "3.1.7 Нагрузка на сервер", "3.1.7", 0, 0, 0, "perm");

            addRuleRow(table, uuid, "3.2 Фрикик", "3.2", 2, 20, 2, "d");

            addRuleRow(table, uuid, "3.3.1 Длинная цепочка маршрутов", "3.3.1", 1, 3, 1, "d");

            addRuleRow(table, uuid, "3.3.2 Процессоры (порча юнитов)", "3.3.2", 1, 90, 7, "d");

            addRuleRow(table, uuid, "3.3.3 Неверные расчёты соотношений", "3.3.3", 1, 90, 7, "d");

            addRuleRow(table, uuid, "3.5 Оскорбление сервера", "3.5", 3, 30, 1, "d");


        }).grow().row();
    }

    private void addRuleRow(Table table, String uuid, String desc, String ruleId, int min, int max, int def, String unit) {
        table.table(row -> {
            row.background(Tex.underline);

            if (unit.equals("perm")) {
                row.button("[red]" + desc, () -> executeBan(uuid, "perm", currentScope, ruleId)).height(60).growX();
                return;
            }

            final int[] currentVal = {def};
            TextButton btn = row.button("[red]" + desc, () -> {
                String finalTime = (currentVal[0] > max) ? "perm" : (currentVal[0] + unit);
                executeBan(uuid, finalTime, currentScope, ruleId);
            }).height(60).width(350).get();
            btn.getLabel().setWrap(true);

            row.table(s -> {
                Label l = s.add("").width(70).get();
                Runnable updateLabel = () -> l.setText(currentVal[0] > max ? "[accent]perm" : currentVal[0] + unit);
                updateLabel.run();

                s.slider(min, max + 1, 1, currentVal[0], v -> {
                    currentVal[0] = (int)v;
                    updateLabel.run();
                }).width(120);
            }).padLeft(10);
        }).margin(5).row();
    }

    private void executeBan(String uuid, String time, String scope, String reason) {
        String cmd = Strings.format("/ban @ @ @ @", uuid, time, scope, reason);
        Call.sendChatMessage(cmd);
        Vars.player.sendMessage("[gray][Sent]: [white]" + cmd);
        hide();
    }
}