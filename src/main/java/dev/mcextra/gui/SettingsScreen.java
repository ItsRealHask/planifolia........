package dev.mcextra.gui;

import com.mojang.blaze3d.platform.Window;
import dev.mcextra.Inputs;
import dev.mcextra.config.Bind;
import dev.mcextra.config.BindInput;
import dev.mcextra.config.ModConfig;
import dev.mcextra.config.Profile;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Окно мода с вкладками: Бинды | Клачи | Эксперимент | Слот | Сворды | Стрела | Жемчуг.
 *
 * Бинды: профили сверху, 10 строк «Бинд N | клавиша/мышь | действие».
 * Клик по окну клавиши -> нажми клавишу или кнопку мыши (Esc = очистить).
 * Клик по окну действия -> следующее действие игры (Shift+клик -> предыдущее).
 */
public class SettingsScreen extends Screen {
	private enum Tab {
		BINDS("Бинды"), CLUTCH("Клачи"), EXPERIMENT("Эксперимент"), SLOT("Слот"), SWORD("Сворды"), ARROW("Стрела"), PEARL("Жемчуг");

		final String title;

		Tab(String title) {
			this.title = title;
		}
	}

	private record Label(String text, int x, int y, int color) {
	}

	private static final int PANEL_BG = 0xFF7A1CFF;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFE0D0FF;
	private static final int KEY_W = 96;
	private static final int LABEL_W = 56;
	private static final int FIELD_W = 80;

	private static Tab currentTab = Tab.BINDS;

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private final Button[] keyButtons = new Button[ModConfig.BIND_COUNT];
	private final Button[] targetButtons = new Button[ModConfig.BIND_COUNT];
	private List<KeyMapping> targets = new ArrayList<>();
	private EditBox nameBox;

	private int listening = -1;
	private boolean armed;
	private int panelX, panelY, panelW, panelH, rowH, contentTop, rowsTop, keyColX, targetColX;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Planifolia"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labels.clear();
		swatches.clear();
		ModConfig cfg = ModConfig.get();
		targets = Inputs.allTargets();

		panelW = Math.min(this.width - 16, 520);
		rowH = Mth.clamp((this.height - 150) / ModConfig.BIND_COUNT, 14, 22);
		panelH = 102 + rowH * ModConfig.BIND_COUNT;
		panelX = (this.width - panelW) / 2;
		panelY = Math.max(4, (this.height - panelH) / 2);
		contentTop = panelY + 34;

		// --- вкладки ---
		Tab[] tabs = Tab.values();
		int tabW = (panelW - 16 - 4 * (tabs.length - 1)) / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			final Tab tab = tabs[i];
			Button button = Button.builder(Component.literal(tab.title), b -> switchTab(tab))
				.bounds(panelX + 8 + i * (tabW + 4), panelY + 8, tabW, 20).build();
			button.active = tab != currentTab;
			addRenderableWidget(button);
		}

		switch (currentTab) {
			case BINDS -> initBinds(cfg);
			case CLUTCH -> initClutch(cfg);
			case EXPERIMENT -> initExperiment(cfg);
			case SLOT -> initSlot(cfg);
			case SWORD -> initSword(cfg);
			case ARROW -> initArrow(cfg);
			case PEARL -> initPearl(cfg);
		}

		addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
			.bounds(panelX + (panelW - 100) / 2, panelY + panelH - 28, 100, 20).build());
	}

	private void switchTab(Tab tab) {
		ModConfig.save();
		currentTab = tab;
		listening = -1;
		rebuildWidgets();
	}

	// ================================================================ вкладка «Бинды»

	private void initBinds(ModConfig cfg) {
		// --- профили ---
		int barY = contentTop;
		int x = panelX + 8;
		int total = panelW - 16;
		int nameW = total - 4 * 20 - 4 * 2;

		addRenderableWidget(Button.builder(Component.literal("<"), b -> switchProfile(-1))
			.bounds(x, barY, 20, 20).build());
		x += 22;

		nameBox = new EditBox(this.font, x, barY, nameW, 20, Component.literal("Профиль"));
		nameBox.setMaxLength(24);
		nameBox.setValue(cfg.active().name);
		nameBox.setResponder(value -> ModConfig.get().active().name = value);
		addRenderableWidget(nameBox);
		x += nameW + 2;

		addRenderableWidget(Button.builder(Component.literal(">"), b -> switchProfile(1))
			.bounds(x, barY, 20, 20).build());
		x += 22;
		addRenderableWidget(Button.builder(Component.literal("+"), b -> addProfile())
			.bounds(x, barY, 20, 20).build());
		x += 22;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> deleteProfile())
			.bounds(x, barY, 20, 20).build());

		// --- бинды ---
		rowsTop = contentTop + 34;
		keyColX = panelX + 8 + LABEL_W;
		targetColX = keyColX + KEY_W + 4;
		int targetW = panelX + panelW - 8 - targetColX;
		int btnH = rowH - 2;

		labels.add(new Label("Клавиша / мышь", keyColX, rowsTop - 11, WHITE));
		labels.add(new Label("Действие (Shift+клик — назад)", targetColX, rowsTop - 11, WHITE));

		for (int i = 0; i < ModConfig.BIND_COUNT; i++) {
			final int index = i;
			int y = rowsTop + i * rowH;

			labels.add(new Label("Бинд " + (i + 1), panelX + 10, y + (rowH - 10) / 2, WHITE));

			keyButtons[i] = Button.builder(Component.empty(), b -> startListening(index))
				.bounds(keyColX, y, KEY_W, btnH).build();
			addRenderableWidget(keyButtons[i]);

			targetButtons[i] = Button.builder(Component.empty(), b -> cycleTarget(index))
				.bounds(targetColX, y, targetW, btnH).build();
			addRenderableWidget(targetButtons[i]);

			refreshRow(i);
		}
	}

	private void switchProfile(int dir) {
		ModConfig cfg = ModConfig.get();
		cfg.activeProfile = Math.floorMod(cfg.activeProfile + dir, cfg.profiles.size());
		listening = -1;
		ModConfig.save();
		rebuildWidgets();
	}

	private void addProfile() {
		ModConfig cfg = ModConfig.get();
		cfg.profiles.add(new Profile("Профиль " + (cfg.profiles.size() + 1)));
		cfg.activeProfile = cfg.profiles.size() - 1;
		listening = -1;
		ModConfig.save();
		rebuildWidgets();
	}

	private void deleteProfile() {
		ModConfig cfg = ModConfig.get();
		if (cfg.profiles.size() <= 1) {
			return;
		}
		cfg.profiles.remove(cfg.activeProfile);
		cfg.activeProfile = Math.min(cfg.activeProfile, cfg.profiles.size() - 1);
		listening = -1;
		ModConfig.save();
		rebuildWidgets();
	}

	private Bind bind(int index) {
		return ModConfig.get().active().binds[index];
	}

	private void refreshRow(int i) {
		Bind bind = bind(i);

		Component keyText;
		if (listening == i) {
			keyText = Component.literal("> ... <");
		} else if (bind.input == null) {
			keyText = Component.literal("—");
		} else {
			keyText = bind.input.displayName();
		}
		keyButtons[i].setMessage(keyText);

		KeyMapping target = Inputs.findTarget(bind.target);
		targetButtons[i].setMessage(target == null
			? Component.literal("—")
			: Component.translatable(target.getName()));
	}

	private void refreshAll() {
		for (int i = 0; i < ModConfig.BIND_COUNT; i++) {
			refreshRow(i);
		}
	}

	private void startListening(int index) {
		listening = index;
		armed = false;
		refreshAll();
	}

	private void setInput(int index, BindInput input) {
		bind(index).input = input;
		listening = -1;
		ModConfig.save();
		refreshAll();
	}

	private void cycleTarget(int index) {
		if (targets.isEmpty()) {
			return;
		}
		boolean back = Inputs.isKey(this.minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
			|| Inputs.isKey(this.minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);

		Bind bind = bind(index);
		int current = 0; // 0 = «нет», 1..n = действие из списка
		for (int i = 0; i < targets.size(); i++) {
			if (targets.get(i).getName().equals(bind.target)) {
				current = i + 1;
				break;
			}
		}
		int next = Math.floorMod(current + (back ? -1 : 1), targets.size() + 1);
		bind.target = next == 0 ? null : targets.get(next - 1).getName();
		ModConfig.save();
		refreshRow(index);
	}

	// ================================================================ вкладка «Клачи»

	private void initClutch(ModConfig cfg) {
		ModConfig.Clutch90 c90 = cfg.clutch90;
		addToggle("Клач 90° (пёрл вверх)", 0, () -> c90.enabled, v -> c90.enabled = v);
		addNumber("   Пауза, мс", 1, c90.delayMs, false, v -> c90.delayMs = (int) Math.round(v));
		addNumber("   Допуск угла, °", 2, c90.tolerance, true, v -> c90.tolerance = v);

		ModConfig.Clutch45 c45 = cfg.clutch45;
		addToggle("Клач 45° (spearmace)", 3, () -> c45.enabled, v -> c45.enabled = v);
		addNumber("   Пауза, мс", 4, c45.delayMs, false, v -> c45.delayMs = (int) Math.round(v));
		addNumber("   Допуск угла, °", 5, c45.tolerance, true, v -> c45.tolerance = v);
		addNumber("   Ниже на, °", 6, c45.offsetDeg, true, v -> c45.offsetDeg = v);
		addNumber("   Поворот камеры, мс", 7, c45.durationMs, false, v -> c45.durationMs = (int) Math.round(v));
	}

	// ================================================================ вкладка «Эксперимент»

	private void initExperiment(ModConfig cfg) {
		ModConfig.Experiment ex = cfg.experiment;
		addToggle("Авто-расчёт точки", 0, () -> ex.enabled, v -> ex.enabled = v);
		addNumber("Угол пёрла вверх, °", 1, ex.angleUp, true, v -> ex.angleUp = v);
		addNumber("Допуск угла, °", 2, ex.tolerance, true, v -> ex.tolerance = v);
		addNumber("Пауза, мс", 3, ex.delayMs, false, v -> ex.delayMs = (int) Math.round(v));
		addNumber("Поворот камеры, мс", 4, ex.durationMs, false, v -> ex.durationMs = (int) Math.round(v));

		labels.add(new Label("Мод считает, куда бросить заряд ветра,", panelX + 10, rowY(6), GRAY));
		labels.add(new Label("чтобы он догнал твой летящий пёрл.", panelX + 10, rowY(6) + 11, GRAY));
		labels.add(new Label("Работает вместо клача 45° для этого угла.", panelX + 10, rowY(6) + 22, GRAY));
	}

	// ================================================================ вкладка «Слот»

	private void initSlot(ModConfig cfg) {
		labels.add(new Label("Слот хотбара с зарядом ветра:", panelX + 10, rowY(0), WHITE));
		labels.add(new Label("Авто = искать заряд ветра по всему хотбару.", panelX + 10, rowY(0) + 12, GRAY));

		int gap = 2;
		int w = (panelW - 16 - 9 * gap) / 10;
		int y = rowY(2) + 6;
		for (int i = 0; i <= 9; i++) {
			final int slot = i;
			Button button = Button.builder(Component.literal(i == 0 ? "Авто" : String.valueOf(i)), b -> {
				cfg.windSlot = slot;
				ModConfig.save();
				rebuildWidgets();
			}).bounds(panelX + 8 + i * (w + gap), y, w, 20).build();
			button.active = cfg.windSlot != slot;
			addRenderableWidget(button);
		}
	}

	// ================================================================ вкладка «Сворды»

	private record Swatch(EditBox box, java.util.function.Supplier<Integer> rgb) {
	}

	private final List<Swatch> swatches = new ArrayList<>();

	/** Поле цвета RRGGBB с образцом слева. */
	private void addColor(String label, int row, java.util.function.Supplier<String> get, Consumer<String> set) {
		int y = rowY(row);
		labels.add(new Label(label, panelX + 10, y + 5, WHITE));
		EditBox box = new EditBox(this.font, panelX + panelW - 8 - FIELD_W, y, FIELD_W, 18, Component.literal(label));
		box.setMaxLength(6);
		box.setFilter(s -> s.matches("[0-9a-fA-F]{0,6}"));
		box.setValue(get.get());
		box.setResponder(s -> {
			if (s.length() == 6) {
				set.accept(s.toUpperCase());
			}
		});
		addRenderableWidget(box);
		swatches.add(new Swatch(box, () -> {
			try {
				return Integer.parseInt(get.get(), 16) & 0xFFFFFF;
			} catch (RuntimeException e) {
				return 0;
			}
		}));
	}

	// ================================================================ вкладка «Жемчуг»

	private void initPearl(ModConfig cfg) {
		ModConfig.PearlPredict pe = cfg.pearl;
		addToggle("Линия жемчуга", 0, () -> pe.enabled, v -> pe.enabled = v);
		addToggle("Учитывать игроков", 1, () -> pe.includePlayers, v -> pe.includePlayers = v);
		addNumber("Дальность прицела, блоков", 2, pe.aimRange, true, v -> pe.aimRange = v);
		addNumber("Толщина линии, пикс (1-10)", 3, pe.lineWidth, false, v -> pe.lineWidth = (int) Math.round(v));
		addColor("Цвет линии (RRGGBB)", 4, () -> pe.lineColor, v -> pe.lineColor = v);
		addNumber("Прозрачность линии, %", 5, pe.lineAlpha, false, v -> pe.lineAlpha = (int) Math.round(v));
		addColor("Цвет маркера (RRGGBB)", 6, () -> pe.markerColor, v -> pe.markerColor = v);

		labels.add(new Label("Пока в руке эндер-жемчуг и прицел наведён на игрока,", panelX + 10, rowY(8), GRAY));
		labels.add(new Label("рисуется траектория броска и маркер в точке падения.", panelX + 10, rowY(8) + 11, GRAY));
		labels.add(new Label("Учитывать игроков: линия обрывается на игроке; выкл - только блоки.", panelX + 10, rowY(8) + 22, GRAY));
	}

	// ================================================================ вкладка «Стрела»

	private void initArrow(ModConfig cfg) {
		ModConfig.ArrowPredict ar = cfg.arrow;
		addToggle("Предсказание стрелы", 0, () -> ar.enabled, v -> ar.enabled = v);
		addNumber("Толщина линии, пикс (1-10)", 1, ar.lineWidth, false, v -> ar.lineWidth = (int) Math.round(v));
		addColor("Цвет линии (RRGGBB)", 2, () -> ar.lineColor, v -> ar.lineColor = v);
		addNumber("Прозрачность линии, %", 3, ar.lineAlpha, false, v -> ar.lineAlpha = (int) Math.round(v));
		addColor("Цвет грани (RRGGBB)", 4, () -> ar.faceColor, v -> ar.faceColor = v);
		addNumber("Прозрачность грани, %", 5, ar.faceAlpha, false, v -> ar.faceAlpha = (int) Math.round(v));

		labels.add(new Label("Работает с луком (при натяжении) и заряженным арбалетом.", panelX + 10, rowY(7), GRAY));
		labels.add(new Label("Линия рисуется всегда, грань блока - только при попадании.", panelX + 10, rowY(7) + 11, GRAY));
		labels.add(new Label("Существа игнорируются, сквозь стены ничего не видно.", panelX + 10, rowY(7) + 22, GRAY));
	}

	private void initSword(ModConfig cfg) {
		ModConfig.SwordBox sw = cfg.sword;
		addToggle("Хитбоксы игроков", 0, () -> sw.enabled, v -> sw.enabled = v);
		addToggle("Только с мечом в руке", 1, () -> sw.swordOnly, v -> sw.swordOnly = v);
		addNumber("Дистанция от, блоков", 2, sw.minDist, true, v -> sw.minDist = v);
		addNumber("Дистанция до, блоков", 3, sw.maxDist, true, v -> sw.maxDist = v);
		addNumber("Толщина линий, 1-4", 4, sw.thickness, false, v -> sw.thickness = (int) Math.round(v));

		addColor("Цвет (RRGGBB)", 5, () -> sw.color, v -> sw.color = v);

		labels.add(new Label("Рамка вокруг игроков, пока дистанция до них", panelX + 10, rowY(7), GRAY));
		labels.add(new Label("(от глаз до ближайшей точки хитбокса) в диапазоне.", panelX + 10, rowY(7) + 11, GRAY));
		labels.add(new Label("Пример цвета: FF3030 красный, 30FF30 зелёный, 00FFFF голубой.", panelX + 10, rowY(7) + 22, GRAY));
	}

	// ================================================================ помощники для вкладок

	private int rowY(int row) {
		return contentTop + 6 + row * 20;
	}

	private void addToggle(String label, int row, BooleanSupplier get, Consumer<Boolean> set) {
		int y = rowY(row);
		labels.add(new Label(label, panelX + 10, y + 5, WHITE));
		Button button = Button.builder(Component.literal(get.getAsBoolean() ? "ВКЛ" : "ВЫКЛ"), b -> {
			set.accept(!get.getAsBoolean());
			b.setMessage(Component.literal(get.getAsBoolean() ? "ВКЛ" : "ВЫКЛ"));
			ModConfig.save();
		}).bounds(panelX + panelW - 8 - FIELD_W, y, FIELD_W, 18).build();
		addRenderableWidget(button);
	}

	private void addNumber(String label, int row, double value, boolean decimal, Consumer<Double> set) {
		int y = rowY(row);
		labels.add(new Label(label, panelX + 10, y + 5, WHITE));
		EditBox box = new EditBox(this.font, panelX + panelW - 8 - FIELD_W, y, FIELD_W, 18, Component.literal(label));
		box.setFilter(s -> s.matches(decimal ? "\\d{0,3}([.,]\\d{0,2})?" : "\\d{0,4}"));
		box.setValue(value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value));
		box.setResponder(s -> {
			try {
				set.accept(Double.parseDouble(s.replace(',', '.')));
			} catch (NumberFormatException ignored) {
				// пустое поле или одна точка: пока ничего не меняем
			}
		});
		addRenderableWidget(box);
	}

	// ================================================================ ввод

	@Override
	public void tick() {
		super.tick();
		if (listening < 0 || currentTab != Tab.BINDS) {
			return;
		}
		Window window = this.minecraft.getWindow();
		boolean esc = Inputs.isKey(window, GLFW.GLFW_KEY_ESCAPE);
		BindInput pressed = pollPressed(window);

		// ждём, пока отпустят кнопку, которой кликнули по окну
		if (!armed) {
			if (pressed == null && !esc) {
				armed = true;
			}
			return;
		}
		if (esc) {
			setInput(listening, null);
		} else if (pressed != null) {
			setInput(listening, pressed);
		}
	}

	private BindInput pollPressed(Window window) {
		for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
			if (key != GLFW.GLFW_KEY_ESCAPE && Inputs.isKey(window, key)) {
				return new BindInput(false, key);
			}
		}
		for (int button = 0; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
			if (Inputs.isMouse(window, button)) {
				return new BindInput(true, button);
			}
		}
		return null;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		// пока ждём клавишу, Esc = «очистить бинд», а не закрыть экран
		return listening < 0;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ModConfig.save();
		this.minecraft.setScreen(parent);
	}

	// ================================================================ рисование

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF000000);
		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		for (Label label : labels) {
			graphics.drawString(this.font, Component.literal(label.text()), label.x(), label.y(), label.color());
		}
		for (Swatch sw : swatches) {
			// образец цвета слева от поля
			int sx = sw.box().getX() - 24;
			int sy = sw.box().getY();
			graphics.fill(sx - 1, sy - 1, sx + 19, sy + 19, 0xFF000000);
			graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF000000 | sw.rgb().get());
		}
	}
}
