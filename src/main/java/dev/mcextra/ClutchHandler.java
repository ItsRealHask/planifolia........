package dev.mcextra;

import dev.mcextra.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Клачи: игрок сам бросает эндер-жемчуг, мод бросает заряд ветра.
 *
 *  - C90: пёрл брошен строго вверх -> через паузу заряд ветра.
 *  - C45: пёрл брошен под ~45° -> камера плавно опускается на N градусов -> заряд ветра.
 *  - EXPERIMENT: как C45, но точка рассчитывается по реальному положению летящего пёрла.
 *
 * Работает только со СВОИМ пёрлом (ищется пёрл, появившийся у глаз игрока в момент броска).
 * Камера остаётся там, где был брошен заряд. Пока клач идёт, мышь заблокирована.
 * Время считается в тиках (1 тик = 50 мс): значения «мс» округляются до ближайшего тика.
 */
public final class ClutchHandler {
	private enum Mode { C90, C45, EXPERIMENT }

	private enum Phase { IDLE, WAIT_PEARL, DELAY, ROTATE }

	private static final double WIND_SPEED = 1.5; // блоков/тик у брошенного заряда ветра
	private static final double PEARL_GRAVITY = 0.03;
	private static final double PEARL_DRAG = 0.99;

	private static Phase phase = Phase.IDLE;
	private static Mode mode;

	private static boolean useWasDown;
	/** Тик, в котором брошен пёрл, не считаем: отсчёт паузы начинается со следующего тика. */
	private static boolean skipEnd;
	private static int cooldown;

	private static int startTick;
	private static int waitTicks;
	private static int rotateTicks;
	private static int rotateIdx;
	private static int pearlWait;

	private static float startPitch, startYaw, targetPitch, targetYaw;

	private ClutchHandler() {
	}

	/** Блокировать ли поворот камеры мышью (вызывается из миксина). */
	public static boolean isLocked() {
		return phase != Phase.IDLE;
	}

	private static void reset() {
		phase = Phase.IDLE;
		skipEnd = false;
	}

	private static int ticks(int ms) {
		return Math.max(0, Math.round(ms / 50.0f));
	}

	// ------------------------------------------------------------ начало тика: ловим бросок пёрла

	public static void onStart(Minecraft mc) {
		LocalPlayer player = mc.player;
		boolean useDown = mc.options.keyUse.isDown();
		boolean click = useDown && !useWasDown;
		useWasDown = useDown;

		if (cooldown > 0) {
			cooldown--;
		}
		if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null) {
			reset();
			return;
		}
		if (phase != Phase.IDLE || !click || cooldown > 0) {
			return;
		}
		if (!player.getMainHandItem().is(Items.ENDER_PEARL) || findWindSlot(player) < 0) {
			return;
		}

		ModConfig cfg = ModConfig.get();
		float pitch = player.getXRot();
		double up = -pitch; // вверх = положительные градусы

		Mode chosen = null;
		if (cfg.experiment.enabled && Math.abs(up - cfg.experiment.angleUp) <= cfg.experiment.tolerance) {
			chosen = Mode.EXPERIMENT;
		} else if (cfg.clutch90.enabled && up >= 90 - cfg.clutch90.tolerance) {
			chosen = Mode.C90;
		} else if (cfg.clutch45.enabled && Math.abs(up - 45) <= cfg.clutch45.tolerance) {
			chosen = Mode.C45;
		}
		if (chosen == null) {
			return;
		}

		mode = chosen;
		skipEnd = true;
		cooldown = 20; // кулдаун пёрла 1 секунда
		startPitch = pitch;
		startYaw = player.getYRot();
		startTick = 0;

		switch (mode) {
			case C90 -> {
				waitTicks = Math.max(1, ticks(cfg.clutch90.delayMs));
				phase = Phase.DELAY;
			}
			case C45 -> {
				waitTicks = ticks(cfg.clutch45.delayMs);
				rotateTicks = Math.max(1, ticks(cfg.clutch45.durationMs));
				targetPitch = Mth.clamp(pitch + (float) cfg.clutch45.offsetDeg, -90f, 90f);
				targetYaw = startYaw;
				rotateIdx = 0;
				phase = Phase.DELAY;
			}
			case EXPERIMENT -> {
				pearlWait = 15; // ждём, пока пёрл появится на клиенте (пинг)
				phase = Phase.WAIT_PEARL;
			}
		}
	}

	// ------------------------------------------------------------ конец тика: выполняем действия

	public static void onEnd(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (phase == Phase.IDLE) {
			return;
		}
		if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null) {
			reset();
			return;
		}
		if (skipEnd) {
			skipEnd = false;
			return;
		}
		ModConfig cfg = ModConfig.get();

		switch (phase) {
			case WAIT_PEARL -> {
				Entity pearl = findOwnPearl(mc, player);
				if (pearl == null) {
					if (--pearlWait <= 0) {
						reset();
					}
					return;
				}
				waitTicks = ticks(cfg.experiment.delayMs);
				rotateTicks = Math.max(1, ticks(cfg.experiment.durationMs));
				rotateIdx = 0;
				computeExperimentTarget(player, pearl, waitTicks + rotateTicks);
				phase = Phase.DELAY;
				// без return: сразу учитываем этот тик как первый тик ожидания
				stepDelay(mc, player);
			}
			case DELAY -> stepDelay(mc, player);
			case ROTATE -> stepRotate(mc, player);
			default -> {
			}
		}
	}

	private static void stepDelay(Minecraft mc, LocalPlayer player) {
		if (--waitTicks > 0) {
			return;
		}
		if (mode == Mode.C90) {
			throwWind(mc, player);
			reset();
		} else {
			phase = Phase.ROTATE;
			stepRotate(mc, player);
		}
	}

	private static void stepRotate(Minecraft mc, LocalPlayer player) {
		rotateIdx++;
		float t = Mth.clamp(rotateIdx / (float) rotateTicks, 0f, 1f);
		float eased = t * t * (3f - 2f * t); // плавный старт и финиш
		float yawDiff = Mth.wrapDegrees(targetYaw - startYaw);
		player.setXRot(Mth.lerp(eased, startPitch, targetPitch));
		player.setYRot(startYaw + yawDiff * eased);
		if (rotateIdx >= rotateTicks) {
			throwWind(mc, player);
			reset();
		}
	}

	// ------------------------------------------------------------ бросок заряда ветра

	private static void throwWind(Minecraft mc, LocalPlayer player) {
		int slot = findWindSlot(player);
		if (slot < 0) {
			return;
		}
		player.getInventory().setSelectedSlot(slot);
		mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		player.swing(InteractionHand.MAIN_HAND);
	}

	/** Слот хотбара (0-8) с зарядом ветра: сначала выбранный в настройках, иначе поиск по хотбару. */
	private static int findWindSlot(LocalPlayer player) {
		int configured = ModConfig.get().windSlot;
		if (configured >= 1 && configured <= 9
			&& player.getInventory().getItem(configured - 1).is(Items.WIND_CHARGE)) {
			return configured - 1;
		}
		for (int i = 0; i < 9; i++) {
			if (player.getInventory().getItem(i).is(Items.WIND_CHARGE)) {
				return i;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------ эксперимент: расчёт точки

	/** Свой пёрл: только что появился, рядом с глазами игрока и летит туда, куда он смотрел. */
	private static Entity findOwnPearl(Minecraft mc, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = Vec3.directionFromRotation(startPitch, startYaw);
		List<Entity> list = mc.level.getEntities(player, player.getBoundingBox().inflate(16),
			e -> e.getType() == EntityType.ENDER_PEARL && e.tickCount <= 25);
		Entity best = null;
		double bestAge = Double.MAX_VALUE;
		for (Entity e : list) {
			Vec3 motion = e.getDeltaMovement();
			if (motion.lengthSqr() < 1.0E-4) {
				continue;
			}
			if (motion.normalize().dot(look) < 0.9) {
				continue; // летит не туда, куда я бросал: чужой
			}
			// пёрл вылетает из точки у глаз; за tickCount тиков он улетел не дальше ~1.6 блока за тик
			if (e.position().distanceTo(eye) > 2.0 + 1.7 * e.tickCount) {
				continue;
			}
			if (e.tickCount < bestAge) {
				bestAge = e.tickCount;
				best = e;
			}
		}
		return best;
	}

	/**
	 * Рассчитывает направление, в которое надо бросить заряд, чтобы он догнал пёрл.
	 * launchIn = через сколько тиков заряд будет брошен (пауза + поворот).
	 */
	private static void computeExperimentTarget(LocalPlayer player, Entity pearl, int launchIn) {
		Vec3 eye = player.getEyePosition();
		Vec3 pos = pearl.position();
		Vec3 vel = pearl.getDeltaMovement();

		// положение пёрла на момент броска заряда
		for (int i = 0; i < launchIn; i++) {
			pos = pos.add(vel);
			vel = new Vec3(vel.x * PEARL_DRAG, vel.y * PEARL_DRAG - PEARL_GRAVITY, vel.z * PEARL_DRAG);
		}

		// ищем первый тик t, когда заряд (скорость WIND_SPEED) успевает долететь до пёрла
		Vec3 aim = pos;
		double bestGap = Double.MAX_VALUE;
		Vec3 p = pos;
		Vec3 v = vel;
		for (int t = 1; t <= 60; t++) {
			p = p.add(v);
			v = new Vec3(v.x * PEARL_DRAG, v.y * PEARL_DRAG - PEARL_GRAVITY, v.z * PEARL_DRAG);
			double gap = p.distanceTo(eye) - WIND_SPEED * t;
			if (Math.abs(gap) < bestGap) {
				bestGap = Math.abs(gap);
				aim = p;
			}
			if (gap <= 0.3) {
				aim = p;
				break;
			}
		}

		Vec3 d = aim.subtract(eye);
		double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
		targetYaw = (float) (Mth.atan2(-d.x, d.z) * (180.0 / Math.PI));
		targetPitch = Mth.clamp((float) (-(Mth.atan2(d.y, horiz) * (180.0 / Math.PI))), -90f, 90f);
	}
}
