package dev.mcextra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mcextra.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Рисует предсказание полёта стрелы в мире (событие WorldRenderEvents.BEFORE_TRANSLUCENT).
 *
 * Тип рендера RenderTypes.debugFilledBox() с тестом глубины: сквозь стены ничего не видно.
 * Линия = ленты (плоские четырёхугольники), повёрнутые к камере; грань блока = полупрозрачный четырёхугольник.
 * Координаты в мире, перед рисованием стек сдвигается на -позицию камеры (как в примере Fabric API).
 */
public final class ArrowPreviewRenderer {
	private ArrowPreviewRenderer() {
	}

	public static void render(WorldRenderContext context) {
		ModConfig.ArrowPredict cfg = ModConfig.get().arrow;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!cfg.enabled || player == null || mc.level == null) {
			return;
		}
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (!ArrowTrajectory.compute(mc, player, pt)) {
			return;
		}

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack poseStack = context.matrices();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = poseStack.last().pose();
		VertexConsumer buf = context.consumers().getBuffer(RenderTypes.debugFilledBox());

		WorldDraw.polyline(buf, m, cam, cfg.lineArgb(), cfg.lineWidth);
		if (ArrowTrajectory.hit) {
			drawFace(buf, m, cam, ArrowTrajectory.hitBox, ArrowTrajectory.hitFace, cfg);
		}

		poseStack.popPose();
	}

	/** Обводка прямоугольника грани (4 ребра) непрозрачным цветом. */
	private static void outline(VertexConsumer buf, Matrix4f m, Vec3 cam, double[] p, int color) {
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			WorldDraw.ribbon(buf, m, cam, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], p[j * 3], p[j * 3 + 1], p[j * 3 + 2], 2.0, color);
		}
	}

	/** Одна грань блока (та, в которую попала стрела): заливка с обеих сторон + обводка. */
	private static void drawFace(VertexConsumer buf, Matrix4f m, Vec3 cam, AABB b, Direction face, ModConfig.ArrowPredict cfg) {
		final double e = 0.004;
		double x0 = b.minX, x1 = b.maxX, y0 = b.minY, y1 = b.maxY, z0 = b.minZ, z1 = b.maxZ;
		double[] q = new double[12]; // 4 угла по кругу
		switch (face) {
			case NORTH -> set(q, x0, y0, z0 - e, x1, y0, z0 - e, x1, y1, z0 - e, x0, y1, z0 - e);
			case SOUTH -> set(q, x1, y0, z1 + e, x0, y0, z1 + e, x0, y1, z1 + e, x1, y1, z1 + e);
			case WEST -> set(q, x0 - e, y0, z1, x0 - e, y0, z0, x0 - e, y1, z0, x0 - e, y1, z1);
			case EAST -> set(q, x1 + e, y0, z0, x1 + e, y0, z1, x1 + e, y1, z1, x1 + e, y1, z0);
			case UP -> set(q, x0, y1 + e, z0, x1, y1 + e, z0, x1, y1 + e, z1, x0, y1 + e, z1);
			case DOWN -> set(q, x0, y0 - e, z1, x1, y0 - e, z1, x1, y0 - e, z0, x0, y0 - e, z0);
		}
		int fill = cfg.faceArgb();
		if (cfg.faceAlpha > 0) {
			for (int i = 0; i < 4; i++) {
				buf.addVertex(m, (float) q[i * 3], (float) q[i * 3 + 1], (float) q[i * 3 + 2]).setColor(fill);
			}
			for (int i = 3; i >= 0; i--) {
				buf.addVertex(m, (float) q[i * 3], (float) q[i * 3 + 1], (float) q[i * 3 + 2]).setColor(fill);
			}
		}
		outline(buf, m, cam, q, 0xFF000000 | (fill & 0xFFFFFF));
	}

	private static void set(double[] q, double... v) {
		System.arraycopy(v, 0, q, 0, 12);
	}
}
