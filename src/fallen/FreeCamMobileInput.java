package fallen;

import arc.Core;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import mindustry.Vars;
import mindustry.input.MobileInput;
import mindustry.gen.Unit;

import static mindustry.Vars.*;

public class FreeCamMobileInput extends MobileInput {

    public boolean freeCamActive = false;
    private final Vec2 camAnchor = new Vec2();

    @Override
    public void update() {
        super.update();

        if (freeCamActive) {
            // Блокируем действия юнита
            if (player.unit() != null) {
                player.unit().vel.setZero();
                player.shooting = false;
            }
            targetPos.set(player.x, player.y);
            movement.setZero();
        }
    }

    @Override
    protected void updateMovement(Unit unit) {
        if (!freeCamActive) {
            super.updateMovement(unit);
        } else {
            // Минимальное обновление, чтобы не было артефактов
            unit.aimLook(player.mouseX, player.mouseY);
            unit.controlWeapons(false, false);
        }
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        // Обрабатываем паннинг ТОЛЬКО если свободная камера активна
        if (!freeCamActive) {
            // Если не наша камера — отдаём обработку родителю
            return super.pan(x, y, deltaX, deltaY);
        }

        // Игнорируем паннинг по интерфейсу
        if (Core.scene.hasMouse(x, y)) {
            return false;
        }

        // Масштабируем дельту под текущий зум камеры (как в оригинале)
        float scale = Core.camera.width / Core.graphics.getWidth();
        deltaX *= scale;
        deltaY *= scale;

        // Двигаем якорь камеры (инверсия: тянем мир → камера плывёт)
        camAnchor.x -= deltaX;
        camAnchor.y -= deltaY;
        Core.camera.position.set(camAnchor);

        // Ограничиваем границами карты
        Core.camera.position.clamp(
                -Core.camera.width/2f, -Core.camera.height/2f,
                world.unitWidth() + Core.camera.width/2f,
                world.unitHeight() + Core.camera.height/2f
        );

        // Возвращаем true, чтобы показать, что мы обработали жест
        return true;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, KeyCode button) {
        // Если свободная камера — не передаём остановку родителю
        if (freeCamActive) {
            return true;
        }
        return super.panStop(x, y, pointer, button);
    }

    // Публичные методы для управления из мода
    public void setFreeCam(boolean active) {
        if (freeCamActive == active) return;

        freeCamActive = active;
        if (active) {
            camAnchor.set(Core.camera.position);
        }
    }

    public boolean isFreeCam() {
        return freeCamActive;
    }
}