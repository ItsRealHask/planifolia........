package dev.mcextra;

import dev.mcextra.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Вкладка «Сворды»: рамка хитбокса вокруг игроков, пока дистанция до них в заданном диапазоне.
 *
 * Рисуется поверх экрана (HUD): ребра коробки проецируются на экран по позиции и повороту камеры.
 * Дистанция = от глаз до ближайшей точки хитбокса цели (так считается досягаемость удара).
 */
public final class SwordHitbox {
	private static final double NEAR = 0.05;

	private SwordHitbox() {
	}

	public static void render(GuiGraphics g, float pt) {
		ModConfig.SwordBox cfg = ModConfig.get().sword;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (!cfg.enabled || me == null || mc.level == null || mc.screen != null || mc.options.hideGui) {
			return;
		}
		if (!mc.options.getCameraType().isFirstPerson()) {
			return; // проекция рассчитана на вид от первого лица
		}
		if (cfg.swordOnly && !me.getMainHandItem().is(ItemTags.SWORDS)) {
			return;
		}

		Vec3 eye = me.getEyePosition(pt);
		float yaw = me.getViewYRot(pt);
		float pitch = me.getViewXRot(pt);

		// базис камеры
		double yr = Math.toRadians(yaw);
		double pr = Math.toRadians(pitch);
		Vec3 fwd = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
		Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
		Vec3 up = right.cross(fwd);

		// вертикальный FOV с учётом спринта/эффектов (как в ванили: настройка FOV и «эффекты поля зрения»)
		double fovDeg = mc.options.fov().get();
		double speedRatio = me.getAttributeValue(Attributes.MOVEMENT_SPEED) / me.getAbilities().getWalkingSpeed();
		double mod = (speedRatio + 1.0) / 2.0;
		if (me.getAbilities().flying) {
			mod *= 1.1;
		}
		double scale = mc.options.fovEffectScale().get();
		fovDeg *= Mth.lerp(scale, 1.0, mod);
		double tanHalf = Math.tan(Math.toRadians(fovDeg) / 2.0);

		int w = g.guiWidth();
		int h = g.guiHeight();
		double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());

		int color = 0xFF000000 | cfg.colorRgb();
		int th = cfg.thickness;

		for (AbstractClientPlayer other : mc.level.players()) {
			if (other == me || other.isSpectator() || !other.isAlive()) {
				continue;
			}
			// положение цели с интерполяцией между тиками
			double dx = Mth.lerp(pt, other.xOld, other.getX()) - other.getX();
			double dy = Mth.lerp(pt, other.yOld, other.getY()) - other.getY();
			double dz = Mth.lerp(pt, other.zOld, other.getZ()) - other.getZ();
			AABB box = other.getBoundingBox().move(dx, dy, dz);

			double ex = Math.max(Math.max(box.minX - eye.x, eye.x - box.maxX), 0);
			double ey = Math.max(Math.max(box.minY - eye.y, eye.y - box.maxY), 0);
			double ez = Math.max(Math.max(box.minZ - eye.z, eye.z - box.maxZ), 0);
			double dist = Math.sqrt(ex * ex + ey * ey + ez * ez);
			if (dist < cfg.minDist || dist > cfg.maxDist) {
				continue;
			}
			if (!me.hasLineOfSight(other)) {
				continue; // за стеной не показываем
			}
			drawBox(g, box, eye, right, up, fwd, tanHalf, aspect, w, h, color, th);
		}
	}

	private static void drawBox(GuiGraphics g, AABB b, Vec3 eye, Vec3 right, Vec3 up, Vec3 fwd,
								double tanHalf, double aspect, int w, int h, int color, int th) {
		double[] xs = {b.minX, b.maxX};
		double[] ys = {b.minY, b.maxY};
		double[] zs = {b.minZ, b.maxZ};
		Vec3[] cam = new Vec3[8]; // координаты углов в системе камеры: (вправо, вверх, вперёд)
		for (int i = 0; i < 8; i++) {
			Vec3 d = new Vec3(xs[i & 1], ys[(i >> 1) & 1], zs[(i >> 2) & 1]).subtract(eye);
			cam[i] = new Vec3(d.dot(right), d.dot(up), d.dot(fwd));
		}
		// ребра: углы, различающиеся ровно в одном бите
		for (int i = 0; i < 8; i++) {
			for (int bit = 1; bit <= 4; bit <<= 1) {
				int j = i | bit;
				if (j != i) {
					segment(g, cam[i], cam[j], tanHalf, aspect, w, h, color, th);
				}
			}
		}
	}

	private static void segment(GuiGraphics g, Vec3 a, Vec3 b, double tanHalf, double aspect,
								int w, int h, int color, int th) {
		if (a.z < NEAR && b.z < NEAR) {
			return;
		}
		// обрезаем по ближней плоскости
		if (a.z < NEAR) {
			a = a.lerp(b, (NEAR - a.z) / (b.z - a.z));
		} else if (b.z < NEAR) {
			b = b.lerp(a, (NEAR - b.z) / (a.z - b.z));
		}
		double ax = w / 2.0 + (a.x / a.z) / (tanHalf * aspect) * (w / 2.0);
		double ay = h / 2.0 - (a.y / a.z) / tanHalf * (h / 2.0);
		double bx = w / 2.0 + (b.x / b.z) / (tanHalf * aspect) * (w / 2.0);
		double by = h / 2.0 - (b.y / b.z) / tanHalf * (h / 2.0);

		double ddx = bx - ax;
		double ddy = by - ay;
		int steps = (int) Math.min(3000, Math.ceil(Math.max(Math.abs(ddx), Math.abs(ddy))));
		for (int i = 0; i <= steps; i++) {
			double t = steps == 0 ? 0 : (double) i / steps;
			int px = (int) Math.round(ax + ddx * t);
			int py = (int) Math.round(ay + ddy * t);
			if (px < -th || py < -th || px > w || py > h) {
				continue;
			}
			g.fill(px, py, px + th, py + th, color);
		}
	}
}
