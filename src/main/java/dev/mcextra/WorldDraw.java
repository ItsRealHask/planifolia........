package dev.mcextra;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Общие помощники для рисования в мире (ленты и маркеры). */
public final class WorldDraw {
	private WorldDraw() {
	}

	/** Линия по массивам точек траектории. */
	public static void polyline(VertexConsumer buf, Matrix4f m, Vec3 cam, int color, double px) {
		double[] xs = ArrowTrajectory.X;
		double[] ys = ArrowTrajectory.Y;
		double[] zs = ArrowTrajectory.Z;
		for (int i = 0; i + 1 < ArrowTrajectory.count; i++) {
			ribbon(buf, m, cam, xs[i], ys[i], zs[i], xs[i + 1], ys[i + 1], zs[i + 1], px, color);
		}
	}

	/** Крестик из трёх осей в точке. */
	public static void cross(VertexConsumer buf, Matrix4f m, Vec3 cam, double x, double y, double z,
							 double r, int color, double px) {
		ribbon(buf, m, cam, x - r, y, z, x + r, y, z, px, color);
		ribbon(buf, m, cam, x, y - r, z, x, y + r, z, px, color);
		ribbon(buf, m, cam, x, y, z - r, x, y, z + r, px, color);
	}

	/**
	 * Плоская лента между двумя точками, развёрнутая к камере. Рисуется с обеих сторон
	 * (на случай, если у типа рендера включена отсечка обратных граней).
	 * px = толщина в пикселях экрана (примерно).
	 */
	public static void ribbon(VertexConsumer buf, Matrix4f m, Vec3 cam,
							   double x0, double y0, double z0, double x1, double y1, double z1, double px, int color) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double dz = z1 - z0;
		double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dl < 1.0E-9) {
			return;
		}
		double vx = (x0 + x1) * 0.5 - cam.x;
		double vy = (y0 + y1) * 0.5 - cam.y;
		double vz = (z0 + z1) * 0.5 - cam.z;
		double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
		// ширина ленты = d x v; если смотрим вдоль линии (почти параллельно), берём запасное направление
		double sx = dy * vz - dz * vy;
		double sy = dz * vx - dx * vz;
		double sz = dx * vy - dy * vx;
		double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1.0E-4 * dl * vl) {
			sx = dz;
			sy = 0;
			sz = -dx;
			len = Math.sqrt(sx * sx + sz * sz);
			if (len < 1.0E-9) {
				sx = 1;
				sy = 0;
				sz = 0;
				len = 1;
			}
		}
		// 1 пиксель на 1080p ~ 0.0013 * расстояние; half = половина ширины
		double half = Math.max(0.004, vl * 0.00065 * px);
		double k = half / len;
		sx *= k;
		sy *= k;
		sz *= k;

		float ax = (float) (x0 + sx), ay = (float) (y0 + sy), az = (float) (z0 + sz);
		float bx = (float) (x1 + sx), by = (float) (y1 + sy), bz = (float) (z1 + sz);
		float cx = (float) (x1 - sx), cy = (float) (y1 - sy), cz = (float) (z1 - sz);
		float ex = (float) (x0 - sx), ey = (float) (y0 - sy), ez = (float) (z0 - sz);

		buf.addVertex(m, ax, ay, az).setColor(color);
		buf.addVertex(m, bx, by, bz).setColor(color);
		buf.addVertex(m, cx, cy, cz).setColor(color);
		buf.addVertex(m, ex, ey, ez).setColor(color);
		buf.addVertex(m, ex, ey, ez).setColor(color);
		buf.addVertex(m, cx, cy, cz).setColor(color);
		buf.addVertex(m, bx, by, bz).setColor(color);
		buf.addVertex(m, ax, ay, az).setColor(color);
	}

}
