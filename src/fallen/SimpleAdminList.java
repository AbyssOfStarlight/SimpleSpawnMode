package fallen;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.KeyCode;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.net.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;


import static fallen.SimpleAdminMode.playerHistory;
import static mindustry.Vars.*;

public class SimpleAdminList{
    public Table content = new Table().marginRight(13f).marginLeft(13f);
    private boolean visible = false;
    private Interval timer = new Interval();
    private TextField search;
    private String manualUuid = "";
    private float button_size = 40f;

    public void build(Group parent){
        content.name = "players";
        parent.fill(cont -> {
            cont.name = "playerlist";
            cont.visible(() -> visible);
            cont.update(() -> {
                if(!(net.active() && state.isGame())){
                    visible = false;
                    return;
                }

                if(visible && timer.get(180)){
//                    if(Core.app.isDesktop()){
//                        if(Core.input.keyDown(KeyCode.mouseLeft) ||
//                                Core.input.keyDown(KeyCode.mouseRight) ||
//                                isMouseOverUI()){
//                            return;
//                        }
//                    }
                    rebuild();
                    content.pack();
                    content.act(Core.graphics.getDeltaTime());
                    Core.app.post(() -> Core.scene.act(Math.min(Core.graphics.getDeltaTime(), 0.033f)));
                }
            });

            cont.table(Tex.buttonTrans, pane -> {
                pane.label(() -> Core.bundle.format(playerHistory.size == 1 ? "players.single" : "players", playerHistory.size));
                pane.row();

                search = pane.field(null, text -> rebuild()).grow().pad(8).name("search").maxTextLength(maxNameLength).get();
                search.setMessageText(Core.bundle.get("players.search"));

                pane.row();
                pane.pane(content).grow().scrollX(false);
                pane.row();

                pane.table(menu -> {
                    menu.defaults().growX().height(50f).fillY();
                    menu.name = "menu";
                    // 3. ПОЛЕ РУЧНОГО ВВОДА
                    menu.table(manual -> {
                        manual.background(Styles.black3).margin(4);

                        // Поле ввода
                        TextField field = manual.field("", text -> manualUuid = text)
                                .growX()
                                .height(45)
                                .get();
                        field.setMessageText("Введите UUID вручную...");

                        // Кнопка открытия меню бана для этого UUID
                        manual.button(Icon.waves, Styles.clearNonei, () -> {
                            Call.sendChatMessage("/freeze " + manualUuid);
                        }).size(45).padLeft(8).tooltip("Заморозить индивидуума");

                        // Кнопка открытия меню бана для этого UUID
                        manual.button(Icon.hammer, Styles.clearNonei, () -> {
                            if (manualUuid.isEmpty()) {
                                ui.showInfoFade("[red]UUID слишком короткий");
                                return;
                            }

                            // Создаем фейкового игрока для заголовка
                            Player fake = Player.create();
                            fake.name = "[gray]Manual Entry[]";

                            new AdvancedBanDialog(fake, manualUuid).show();
                        }).size(45).padLeft(8).tooltip("Открыть меню бана для этого UUID");

                    }).padTop(10).row();
                    menu.button("@close", this::toggle);
                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f).minWidth(360f);
        });

        rebuild();
    }

//    private boolean isMouseOverUI(){
//        // Проверяем, не наведена ли мышь на наш контент
//        var hit = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
//        return hit != null && (content.isDescendantOf(hit) || content == hit);
//    }

    public void rebuild(){

        float h = 50f;
        boolean found = false;


        Seq<PlayerData> filtered = Seq.with(playerHistory.values());
        if(search.getText().length() > 0){
            String query = search.getText().toLowerCase();
            filtered = filtered.copy().retainAll(d ->
                    Strings.stripColors(d.name.toLowerCase()).contains(query)
            );
        }
        filtered.sort((a, b) -> {
            // Онлайн выше оффлайна
            if (a.online != b.online) {
                return a.online ? -1 : 1;
            }
            // Админы выше
            boolean aAdmin = a.player != null && a.player.admin;
            boolean bAdmin = b.player != null && b.player.admin;
            if (aAdmin != bAdmin) {
                return aAdmin ? -1 : 1;
            }
            // По имени
            return Strings.stripColors(a.name).compareToIgnoreCase(Strings.stripColors(b.name));
        });

        content.clear();
        boolean lastWasOnline = true; // Начинаем с true, чтобы не показывать разделитель в начале

        //for(var user : players){
        //for (PlayerData user : SimpleAdminMode.playerHistory.values()) {
        for (PlayerData user : filtered) {
            found = true;

            // Разделитель между онлайн/оффлайн
            if (lastWasOnline && !user.online) {
                content.add().height(8).row();
                content.add("[white]---Оффлайн---").color(Pal.redLight).center().row();
                content.add().height(4).row();
            }
            lastWasOnline = user.online;

            // ОСНОВНАЯ ТАБЛИЦА ИГРОКА
            Table button = new Table();
            button.left();
            button.margin(4).marginBottom(6);
            button.background(Tex.underline);

            // === СТРОКА 1: Имя + Кнопка меню ===
            button.table(nameTable -> {
                nameTable.left().defaults().pad(2);

                // Имя игрока (занимает всё доступное место)
                nameTable.add(user.name).left().growX().wrap();

                if (user.online) {
                    nameTable.button(Icon.refresh, Styles.cleari, () -> {
                        Player p = Groups.player.getByID(user.id);
                        if (p != null) Call.adminRequest(p, Packets.AdminAction.trace, null);
                    }).size(button_size).margin(2f).tooltip("Обновить UUID");
                }

                // Кнопка меню (только для онлайн)
                if (user.online) {
                    nameTable.button(Icon.menu, Styles.cleari, () -> {
                        showPlayerMenu(user); // 👈 Вынесли в отдельный метод
                    }).size(button_size).margin(2f).tooltip("Админ меню");
                }
            }).growX().row();

            // === СТРОКА 2: Статус + UUID + Кнопки ===
            button.table(infoTable -> {
                infoTable.left().defaults().height(28).pad(1);
                // UUID
                String uuidText = user.uuid.equals("admin?") ? "[green]админ" :
                        user.uuid.equals("Загрузка...") ? "[gray]ожидание..." :
                                user.uuid.equals("недоступен") ? "[gray]недоступен" :
                                        user.uuid;
                infoTable.add("[accent]UUID: [white]" + uuidText).growX().left();

                // Кнопки действий
                if (user.online) {
                    infoTable.button(Icon.wavesSmall, Styles.cleari, () -> {
                        if (!user.uuid.equals("Загрузка...") && !user.uuid.equals("недоступен")) {
                            Call.sendChatMessage("/freeze " + user.uuid);
                        } else {
                            ui.showInfoFade("[red]UUID ещё не получен");
                        }
                    }).size(button_size).margin(2f).tooltip("Заморозить");
                }

                infoTable.button(Icon.hammer, Styles.cleari, () -> {
                    if (!user.uuid.equals("Загрузка...") && !user.uuid.equals("недоступен")) {
                        Player p = Groups.player.getByID(user.id);
                        if (p == null) p = Player.create();
                        p.name = user.name;
                        new AdvancedBanDialog(p, user.uuid).show();
                    } else {
                        ui.showInfoFade("[red]UUID ещё не получен");
                    }
                }).size(button_size).margin(2f).tooltip("Бан меню");
            }).growX();

            content.add(button).width(340f).padBottom(4);
            content.row();
        }
        if(!found){
            content.add(Core.bundle.format("players.notfound")).padBottom(6).width(350f).maxHeight(h + 14);
        }

        content.marginBottom(5);

    }

    private void showPlayerMenu(PlayerData user) {
        var dialog = new BaseDialog(user.name);
        dialog.title.setColor(Color.white);
        dialog.titleTable.remove();
        dialog.closeOnBack();

        var bstyle = Styles.defaultt;

        dialog.cont.add(user.name).left().row();
        dialog.cont.image(Tex.whiteui, Pal.accent).fillX().height(3f).pad(4f).row();

        dialog.cont.pane(t -> {
            t.defaults().size(220f, 55f).pad(3f);

            t.button("@player.ban", Icon.hammer, bstyle, () -> {
                ui.showConfirm("@confirm", Core.bundle.format("confirmban", user.name),
                        () -> Call.adminRequest(user.player, AdminAction.ban, null));
                dialog.hide();
            }).row();

            t.button("@player.kick", Icon.cancel, bstyle, () -> {
                ui.showConfirm("@confirm", Core.bundle.format("confirmkick", user.name),
                        () -> Call.adminRequest(user.player, AdminAction.kick, null));
                dialog.hide();
            }).row();

            t.button("@player.trace", Icon.zoom, bstyle, () -> {
                Call.adminRequest(user.player, AdminAction.trace, null);
                dialog.hide();
            }).row();

        }).row();

        dialog.cont.button("@back", Icon.left, dialog::hide).padTop(-1f).size(220f, 55f);
        dialog.show();
    }

    public void toggle(){
        visible = !visible;
        if(visible){
            rebuild();
        }else{
            Core.scene.setKeyboardFocus(null);
            search.clearText();
        }
    }

}