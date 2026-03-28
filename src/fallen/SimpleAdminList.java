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
    private Table mainTable;
    private Table infoPanel;
    private boolean visible = false;
    private Interval timer = new Interval();
    private TextField search;
    private String manualUuid = "";
    private float button_size = 40f;
    private float panelWidth = 400f;

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

            mainTable = cont.table(Tex.buttonTrans, pane -> {
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
                    menu.table(buttons -> {
                        buttons.defaults().height(50f).fillY();
                        buttons.button("@close", this::toggle).growX();
                        buttons.button(Icon.settings, Styles.defaulti, () -> {
                            new SimpleAdminSettings(this).show();
                        }).width(50f).padLeft(4f);
                    }).growX().padLeft(4f);

                }).margin(0f).pad(10f).growX();

            }).touchable(Touchable.enabled).margin(14f).minWidth(panelWidth).get();
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
        this.button_size = Core.settings.getInt("sam-btn-size", 40);
        this.panelWidth = Core.settings.getInt("sam-list-w", 400);
        if (mainTable != null) {
            mainTable.setWidth(panelWidth);
            mainTable.invalidate();
        }
        float buttonWidth = panelWidth - 60f;

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

                nameTable.button(Icon.info, Styles.cleari, () -> {
                    showInfoPanel(user);
                    if(Core.settings.getBool("sam-close-list")){
                        this.toggle();
                    }
                }).size(button_size).margin(2f).tooltip("Просмотреть сохраненные данные");

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

                if(Core.settings.getBool("sam-show-stats", false)) {
                    infoTable.button(st -> {
                        st.defaults().padLeft(2).padRight(2).fontScale(0.8f);
                        st.add(new Label(() -> "[green]" + user.builds + "[]| [red]" + user.breaks + "[]| [sky]" + user.configs)).minWidth(60);
                    }, Styles.flatBordert, () -> {}).right().height(24).padRight(4);
                }
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

            content.add(button).width(buttonWidth).padBottom(4);
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


    private void showInfoPanel(PlayerData data) {
        if(infoPanel != null) infoPanel.remove();

        infoPanel = new Table();
        infoPanel.setFillParent(true);
        infoPanel.touchable = Touchable.enabled;
        infoPanel.clicked(() -> {
            if(Core.settings.getBool("sam-close-outside", true)){
                infoPanel.remove();
                infoPanel = null;
            }
        });

        infoPanel.table(Tex.buttonTrans, t -> {
            t.touchable = Touchable.enabled;
            t.clicked(() -> {});

            t.addListener(new arc.scene.event.InputListener() {
                @Override
                public boolean touchDown(arc.scene.event.InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
                    event.stop();
                    return true;
                }
            });

            t.margin(12).defaults().left();

            t.table(h -> {
                h.add(new Image(Icon.infoSmall)).padRight(8);
                h.add("[accent]Инфо: " + data.name).growX();
                h.button(Icon.leftOpen, Styles.cleari, () -> {
                    infoPanel.remove();
                    infoPanel = null;
                    this.toggle();
                }).size(32);
                h.button(Icon.cancel, Styles.cleari, () -> {
                    infoPanel.remove();
                    infoPanel = null;
                }).size(32);
            }).growX().row();

            t.image().color(Pal.accent).fillX().height(2).padTop(4).padBottom(8).row();

            t.pane(p -> {
                p.defaults().left().growX().margin(2);

                addCopyRow(p, "Name", data.name);
                addCopyRow(p, "UUID", data.uuid);
                addCopyRow(p, "IP", data.ip);
                addCopyRow(p, "Язык", data.locale);
                addCopyRow(p, "Входы", String.valueOf(data.timesJoined));
                addCopyRow(p, "Кики", String.valueOf(data.timesKicked));
                addCopyRow(p, "Мобила", data.mobile ? "Да" : "Нет");
                addCopyRow(p, "Моды", data.modded ? "Да" : "Нет");

                if(data.names.length > 1) {
                    p.add("[gray]История имен:").padTop(8).row();
                    for(String s : data.names) addCopyRow(p, "", s);
                }

                if(data.ips.length > 1) {
                    p.add("[gray]История IP:").padTop(8).row();
                    for(String s : data.ips) addCopyRow(p, "", s);
                }
            }).size(340, 300).row();

            if(data.online) {
                t.button("ОБНОВИТЬ", Icon.refresh, () -> {
                    Player p = Groups.player.getByID(data.id);
                    if(p != null) Call.adminRequest(p, Packets.AdminAction.trace, null);
                    infoPanel.remove();
                }).margin(10).growX().height(45).padTop(10);
            }

        }).center();

        Core.scene.add(infoPanel);
    }

    private void addCopyRow(Table table, String label, String value) {
        if(value == null || value.isEmpty()) return;

        String displayText = label.isEmpty() ? "[white]" + value : "[lightgray]" + label + ": [accent]" + value;

        table.button(b -> {
            b.left().margin(4);
            b.add(displayText).left().wrap().growX().fontScale(0.9f);
        }, Styles.flatBordert, () -> {
            Core.app.setClipboardText(value);
            ui.showInfoFade("[accent]" + (label.isEmpty() ? value : label) + " [white]скопирован");
        }).growX().height(32).padBottom(2).row();
    }

}