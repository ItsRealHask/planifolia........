package dev.mcextra.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Конфиг: профили, бинды, клачи. Хранится в config/moreculling_extra.json. */
public final class ModConfig {
	public static final int BIND_COUNT = 10;

	private static final Logger LOGGER = LoggerFactory.getLogger("moreculling_extra");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	/** Клач 90°: пёрл строго вверх -> заряд ветра через паузу. */
	public static class Clutch90 {
		public boolean enabled = false;
		public int delayMs = 50;
		public double tolerance = 3;
	}

	/** Клач 45°: пёрл под 45° вверх -> камера плавно ниже -> заряд ветра. */
	public static class Clutch45 {
		public boolean enabled = false;
		public int delayMs = 50;
		public double tolerance = 5;
		public double offsetDeg = 5;
		public int durationMs = 100;
	}

	/** Эксперимент: мод сам рассчитывает точку по реальному положению своего пёрла. */
	public static class Experiment {
		public boolean enabled = false;
		/** Угол броска пёрла вверх, в градусах (45 = вверх на 45°). */
		public double angleUp = 45;
		public double tolerance = 5;
		public int delayMs = 50;
		public int durationMs = 100;
	}

	/** Сворды: рамка хитбокса игроков на дистанции minDist..maxDist. */
	public static class SwordBox {
		public boolean enabled = false;
		/** Показывать только когда в руке меч. */
		public boolean swordOnly = true;
		public double minDist = 2.5;
		public double maxDist = 2.9;
		/** Цвет в формате RRGGBB. */
		public String color = "FF3030";
		public int thickness = 1;

		public int colorRgb() {
			try {
				return Integer.parseInt(color, 16) & 0xFFFFFF;
			} catch (RuntimeException e) {
				return 0xFF3030;
			}
		}
	}

	/** Предсказание полёта стрелы (лук / заряженный арбалет). */
	public static class ArrowPredict {
		public boolean enabled = false;
		public String lineColor = "FFFFFF";
		/** Прозрачность линии, 0..100 %. */
		public int lineAlpha = 90;
		/** Толщина линии, 1..10 (1 = 0.01 блока). */
		public int lineWidth = 3;
		public String faceColor = "FF0000";
		/** Прозрачность красной грани, 0..100 %. */
		public int faceAlpha = 40;

		public int lineArgb() {
			return argb(lineAlpha, lineColor, 0xFFFFFF);
		}

		public int faceArgb() {
			return argb(faceAlpha, faceColor, 0xFF0000);
		}

		static int argb(int alphaPercent, String hex, int fallback) {
			int rgb;
			try {
				rgb = Integer.parseInt(hex, 16) & 0xFFFFFF;
			} catch (RuntimeException e) {
				rgb = fallback;
			}
			int a = Math.round(Math.max(0, Math.min(100, alphaPercent)) * 2.55f);
			return (a << 24) | rgb;
		}
	}

	/** Эндер-жемчуг: траектория, когда прицел наведён на игрока. */
	public static class PearlPredict {
		public boolean enabled = false;
		/** Учитывать игроков (пёрл «попадает» в игрока), иначе только блоки. */
		public boolean includePlayers = true;
		/** Дальность поиска игрока по прицелу, блоков. */
		public double aimRange = 100;
		public String lineColor = "B060FF";
		public int lineAlpha = 90;
		public int lineWidth = 3;
		public String markerColor = "00FF66";

		public int lineArgb() {
			return ArrowPredict.argb(lineAlpha, lineColor, 0xB060FF);
		}

		public int markerArgb() {
			return ArrowPredict.argb(100, markerColor, 0x00FF66);
		}
	}

	public SwordBox sword = new SwordBox();
	public PearlPredict pearl = new PearlPredict();
	public ArrowPredict arrow = new ArrowPredict();
	public Clutch90 clutch90 = new Clutch90();
	public Clutch45 clutch45 = new Clutch45();
	public Experiment experiment = new Experiment();

	/** Слот хотбара с зарядом ветра: 0 = искать автоматически, 1..9 = номер слота. */
	public int windSlot = 0;

	public int activeProfile = 0;
	public List<Profile> profiles = new ArrayList<>();

	public static ModConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public Profile active() {
		return profiles.get(activeProfile);
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("moreculling_extra.json");
	}

	public static void load() {
		ModConfig loaded = null;
		try (Reader reader = Files.newBufferedReader(path())) {
			loaded = GSON.fromJson(reader, ModConfig.class);
		} catch (NoSuchFileException ignored) {
			// первый запуск
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Не удалось прочитать конфиг, будет создан новый", e);
		}
		if (loaded == null) {
			loaded = new ModConfig();
		}
		loaded.normalize();
		instance = loaded;
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			get().normalize();
			try (Writer writer = Files.newBufferedWriter(path())) {
				GSON.toJson(get(), writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Не удалось сохранить конфиг", e);
		}
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private void normalize() {
		if (profiles == null) {
			profiles = new ArrayList<>();
		}
		profiles.removeIf(p -> p == null);
		if (profiles.isEmpty()) {
			profiles.add(new Profile("Профиль 1"));
		}
		for (Profile profile : profiles) {
			profile.normalize();
		}
		activeProfile = clamp(activeProfile, 0, profiles.size() - 1);

		if (clutch90 == null) clutch90 = new Clutch90();
		if (clutch45 == null) clutch45 = new Clutch45();
		if (experiment == null) experiment = new Experiment();

		clutch90.delayMs = clamp(clutch90.delayMs, 0, 2000);
		clutch90.tolerance = clamp(clutch90.tolerance, 0, 30);

		clutch45.delayMs = clamp(clutch45.delayMs, 0, 2000);
		clutch45.tolerance = clamp(clutch45.tolerance, 0, 30);
		clutch45.offsetDeg = clamp(clutch45.offsetDeg, 0, 45);
		clutch45.durationMs = clamp(clutch45.durationMs, 50, 2000);

		experiment.angleUp = clamp(experiment.angleUp, 0, 90);
		experiment.tolerance = clamp(experiment.tolerance, 0, 30);
		experiment.delayMs = clamp(experiment.delayMs, 0, 2000);
		experiment.durationMs = clamp(experiment.durationMs, 50, 2000);

		windSlot = clamp(windSlot, 0, 9);

		if (pearl == null) pearl = new PearlPredict();
		pearl.aimRange = clamp(pearl.aimRange, 5, 200);
		pearl.lineAlpha = clamp(pearl.lineAlpha, 0, 100);
		pearl.lineWidth = clamp(pearl.lineWidth, 1, 10);
		if (pearl.lineColor == null || !pearl.lineColor.matches("[0-9a-fA-F]{6}")) pearl.lineColor = "B060FF";
		if (pearl.markerColor == null || !pearl.markerColor.matches("[0-9a-fA-F]{6}")) pearl.markerColor = "00FF66";

		if (arrow == null) arrow = new ArrowPredict();
		arrow.lineAlpha = clamp(arrow.lineAlpha, 0, 100);
		arrow.faceAlpha = clamp(arrow.faceAlpha, 0, 100);
		arrow.lineWidth = clamp(arrow.lineWidth, 1, 10);
		if (arrow.lineColor == null || !arrow.lineColor.matches("[0-9a-fA-F]{6}")) arrow.lineColor = "FFFFFF";
		if (arrow.faceColor == null || !arrow.faceColor.matches("[0-9a-fA-F]{6}")) arrow.faceColor = "FF0000";

		if (sword == null) sword = new SwordBox();
		sword.minDist = clamp(sword.minDist, 0, 6);
		sword.maxDist = clamp(sword.maxDist, sword.minDist, 6);
		sword.thickness = clamp(sword.thickness, 1, 4);
		if (sword.color == null || !sword.color.matches("[0-9a-fA-F]{6}")) {
			sword.color = "FF3030";
		}
	}
}
