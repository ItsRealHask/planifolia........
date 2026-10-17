package dev.mcextra;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * Расчёт траектории стрелы (только клиент, только блоки).
 *
 * Физика как у ванильной стрелы: за тик pos += vel; затем vel *= 0.99 (в воде 0.6); vel.y -= 0.05.
 * Стартовая позиция: глаза - 0.1 по Y. К скорости стрелы добавляется движение игрока (как в ваниле).
 * Разброс не учитывается. Результат хранится в статических массивах, без создания новых списков.
 */
public final class ArrowTrajectory {
	public static final int MAX_TICKS = 200;
	private static final double GRAVITY = 0.05;
	private static final double AIR_DRAG = 0.99;
	private static final double WATER_DRAG = 0.6;
	private static final double CROSSBOW_SPEED = 3.15;

	/** Точки траектории: индексы 0..count-1. */
	public static final double[] X = new double[MAX_TICKS + 2];
	public static final double[] Y = new double[MAX_TICKS + 2];
	public static final double[] Z = new double[MAX_TICKS + 2];
	public static int count;

	/** Попала ли стрела в блок; если да, то в какую грань и какая у неё форма. */
	public static boolean hit;
	public static Direction hitFace;
	public static AABB hitBox;

	private static final BlockPos.MutableBlockPos WATER_POS = new BlockPos.MutableBlockPos();

	private ArrowTrajectory() {
	}

	/** Скорость стрелы для текущего оружия в руках, либо -1, если показывать нечего. */
	public static double currentSpeed(LocalPlayer p) {
		if (p.isUsingItem()) {
			ItemStack use = p.getUseItem();
			if (use.is(Items.BOW)) {
				float t = Math.min(p.getTicksUsingItem() / 20.0f, 1.0f);
				float power = (t * t + t * 2.0f) / 3.0f; // то же, что BowItem.getPowerForTime
				if (power < 0.1f) {
					return -1; // ванильный лук не выстрелит
				}
				return Math.min(power, 1.0f) * 3.0;
			}
			return -1; // например, арбалет ещё заряжается
		}
		if (CrossbowItem.isCharged(p.getMainHandItem()) || CrossbowItem.isCharged(p.getOffhandItem())) {
			return CROSSBOW_SPEED;
		}
		return -1;
	}

	/** Попал ли снаряд в игрока (только если simulate вызван с учётом игроков). */
	public static boolean hitPlayer;

	/** Траектория стрелы для оружия в руках. Возвращает false, если рисовать нечего. */
	public static boolean compute(Minecraft mc, LocalPlayer p, float partialTick) {
		double speed = currentSpeed(p);
		if (speed < 0 || mc.level == null) {
			return false;
		}
		simulate(mc, p, partialTick, speed, GRAVITY, AIR_DRAG, WATER_DRAG, MAX_TICKS, false);
		return true;
	}

	/**
	 * Общая симуляция снаряда: старт = глаза - 0.1, направление = взгляд, к скорости добавляется движение игрока.
	 * Каждый тик: pos += vel; vel *= drag (в воде waterDrag); vel.y -= gravity.
	 * Блоки: луч между позициями (форма столкновения). Игроки (опционально): хитбокс + 0.3, как у снарядов.
	 */
	public static void simulate(Minecraft mc, LocalPlayer p, float partialTick, double speed,
								double gravity, double airDrag, double waterDrag, int maxTicks, boolean withPlayers) {
		Level level = mc.level;

		// направление взгляда
		double yaw = Math.toRadians(p.getViewYRot(partialTick));
		double pitch = Math.toRadians(p.getViewXRot(partialTick));
		double dx = -Math.sin(yaw) * Math.cos(pitch);
		double dy = -Math.sin(pitch);
		double dz = Math.cos(yaw) * Math.cos(pitch);

		// старт: глаза - 0.1; скорость = направление * скорость + движение игрока (Y только в воздухе)
		Vec3 eye = p.getEyePosition(partialTick);
		Vec3 mv = p.getDeltaMovement();
		double px = eye.x;
		double py = eye.y - 0.1;
		double pz = eye.z;
		double vx = dx * speed + mv.x;
		double vy = dy * speed + (p.onGround() ? 0.0 : mv.y);
		double vz = dz * speed + mv.z;

		count = 0;
		hit = false;
		hitPlayer = false;
		hitFace = null;
		hitBox = null;
		X[count] = px;
		Y[count] = py;
		Z[count] = pz;
		count++;

		double minY = level.getMinY();
		int limit = Math.min(maxTicks, MAX_TICKS);
		for (int tick = 0; tick < limit; tick++) {
			double nx = px + vx;
			double ny = py + vy;
			double nz = pz + vz;
			Vec3 from = new Vec3(px, py, pz);
			Vec3 to = new Vec3(nx, ny, nz);

			// луч между прошлой и новой позицией, форма столкновения блока учитывается
			BlockHitResult r = level.clip(new ClipContext(from, to,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			boolean blockHit = r.getType() != HitResult.Type.MISS;
			double blockDist = blockHit ? r.getLocation().distanceToSqr(from) : Double.MAX_VALUE;

			// игроки (не мы сами)
			Vec3 playerPoint = null;
			if (withPlayers) {
				double best = blockDist;
				for (Player other : level.players()) {
					if (other == p || other.isSpectator() || !other.isAlive()) {
						continue;
					}
					Optional<Vec3> c = other.getBoundingBox().inflate(0.3).clip(from, to);
					if (c.isPresent()) {
						double d = c.get().distanceToSqr(from);
						if (d < best) {
							best = d;
							playerPoint = c.get();
						}
					}
				}
			}

			if (playerPoint != null) {
				X[count] = playerPoint.x;
				Y[count] = playerPoint.y;
				Z[count] = playerPoint.z;
				count++;
				hit = true;
				hitPlayer = true;
				return;
			}
			if (blockHit) {
				Vec3 loc = r.getLocation();
				X[count] = loc.x;
				Y[count] = loc.y;
				Z[count] = loc.z;
				count++;
				hit = true;
				hitFace = r.getDirection();
				BlockPos pos = r.getBlockPos();
				BlockState state = level.getBlockState(pos);
				VoxelShape shape = state.getCollisionShape(level, pos);
				hitBox = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
				return;
			}

			px = nx;
			py = ny;
			pz = nz;
			X[count] = px;
			Y[count] = py;
			Z[count] = pz;
			count++;

			WATER_POS.set(px, py, pz);
			double drag = level.getFluidState(WATER_POS).is(FluidTags.WATER) ? waterDrag : airDrag;
			vx *= drag;
			vy = vy * drag - gravity;
			vz *= drag;

			if (py < minY) {
				break; // упал ниже мира
			}
		}
	}
}
