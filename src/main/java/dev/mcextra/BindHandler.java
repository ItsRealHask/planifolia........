package dev.mcextra;

import com.mojang.blaze3d.platform.Window;
import dev.mcextra.config.Bind;
import dev.mcextra.config.ModConfig;
import dev.mcextra.gui.SettingsScreen;
import dev.mcextra.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Каждый тик: открывает экран по сочетанию и «нажимает» действия по активному профилю. */
public final class BindHandler {
	private static final KeyMapping[] HELD = new KeyMapping[ModConfig.BIND_COUNT];
	private static boolean comboWasDown;

	private BindHandler() {
	}

	public static void tick(Minecraft mc) {
		Window window = mc.getWindow();

		// Сочетание O + P открывает настройки (только в игре, не поверх других экранов)
		boolean combo = Inputs.isKey(window, GLFW.GLFW_KEY_O)
			&& Inputs.isKey(window, GLFW.GLFW_KEY_P);
		if (combo && !comboWasDown && mc.screen == null && mc.player != null) {
			mc.setScreen(new SettingsScreen(null));
		}
		comboWasDown = combo;

		boolean active = mc.screen == null && mc.player != null;
		Bind[] binds = ModConfig.get().active().binds;

		for (int i = 0; i < ModConfig.BIND_COUNT; i++) {
			Bind bind = binds[i];
			KeyMapping target = Inputs.findTarget(bind.target);
			boolean down = active && bind.input != null && target != null && Inputs.isDown(window, bind.input);

			KeyMapping held = HELD[i];
			if (held != null && (!down || held != target)) {
				held.setDown(false);
				HELD[i] = null;
				held = null;
			}
			if (down) {
				if (held == null) {
					KeyMappingAccessor accessor = (KeyMappingAccessor) target;
					accessor.mce$setClickCount(accessor.mce$getClickCount() + 1);
					HELD[i] = target;
				}
				target.setDown(true);
			}
		}
	}
}
