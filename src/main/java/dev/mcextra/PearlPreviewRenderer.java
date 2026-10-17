package dev.mcextra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mcextra.config.ModConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Вкладка «Жемчуг»: пока в руке эндер-жемчуг и прицел наведён на игрока, рисуется траектория пёрла
 * (от точки вылета, с физикой пёрла) и маркер в точке падения. Только клиент, с проверкой глубины.
 *
 * Физика ThrownEnderpearl: скорость 1.5, pos += vel; vel *= 0.99 (в воде 0.8); vel.y -= 0.03. Разброс не учитывается.
 */
public final class PearlPreviewRenderer {
	private static final double SPEED = 1.5;
	private static final double GRAVITY = 0.03;
	private static final double AIR_DRAG = 0.99;
	private static final double WATER_DRAG = 0.8;
	private static final int MAX_TICKS = 200;

	private PearlPreviewRenderer() {
	}

	public static void render(WorldRenderContext context) {
		ModConfig.PearlPredict cfg = ModConfig.get().pearl;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!cfg.enabled || player == null || mc.level == null) {
			return;
		}
		if (!player.getMainHandItem().is(Items.ENDER_PEARL) && !player.getOffhandItem().is(Items.ENDER_PEARL)) {
			return;
		}
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (!aimedAtPlayer(mc, player, pt, cfg.aimRange)) {
			return;
		}

		ArrowTrajectory.simulate(mc, player, pt, SPEED, GRAVITY, AIR_DRAG, WATER_DRAG, MAX_TICKS, cfg.includePlayers);

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack poseStack = context.matrices();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = poseStack.last().pose();
		VertexConsumer buf = context.consumers().getBuffer(RenderTypes.debugFilledBox());

		WorldDraw.polyline(buf, m, cam, cfg.lineArgb(), cfg.lineWidth);
		if (ArrowTrajectory.hit && ArrowTrajectory.count > 0) {
			int i = ArrowTrajectory.count - 1;
			WorldDraw.cross(buf, m, cam, ArrowTrajectory.X[i], ArrowTrajectory.Y[i], ArrowTrajectory.Z[i],
				0.3, cfg.markerArgb(), 3.0);
		}

		poseStack.popPose();
	}

	/** Прицел (прямой луч взгляда) на хитбоксе игрока ближе первого блока и в пределах дальности. */
	private static boolean aimedAtPlayer(Minecraft mc, LocalPlayer me, float pt, double range) {
		Vec3 eye = me.getEyePosition(pt);
		Vec3 end = eye.add(me.getViewVector(pt).scale(range));

		BlockHitResult block = mc.level.clip(new ClipContext(eye, end,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
		double blockDist = block.getType() != HitResult.Type.MISS
			? block.getLocation().distanceToSqr(eye) : Double.MAX_VALUE;

		for (AbstractClientPlayer other : mc.level.players()) {
			if (other == me || other.isSpectator() || !other.isAlive()) {
				continue;
			}
			Optional<Vec3> c = other.getBoundingBox().inflate(0.1).clip(eye, end);
			if (c.isPresent() && c.get().distanceToSqr(eye) < blockDist) {
				return true;
			}
		}
		return false;
	}
}
