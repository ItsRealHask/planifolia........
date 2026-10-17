package dev.mcextra;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.mcextra.config.BindInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Мелкие помощники: опрос клавиш/мыши и список ванильных действий. */
public final class Inputs {
	private Inputs() {
	}

	public static boolean isKey(Window window, int keyCode) {
		return InputConstants.isKeyDown(window, keyCode);
	}

	public static boolean isMouse(Window window, int button) {
		return GLFW.glfwGetMouseButton(window.handle(), button) == GLFW.GLFW_PRESS;
	}

	public static boolean isDown(Window window, BindInput input) {
		return input.mouse ? isMouse(window, input.code) : isKey(window, input.code);
	}

	/** Все ванильные (и чужие) действия игры, отсортированные по категории. */
	public static List<KeyMapping> allTargets() {
		KeyMapping[] all = Minecraft.getInstance().options.keyMappings.clone();
		Arrays.sort(all);
		return new ArrayList<>(Arrays.asList(all));
	}

	public static KeyMapping findTarget(String name) {
		if (name == null) {
			return null;
		}
		for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
			if (mapping.getName().equals(name)) {
				return mapping;
			}
		}
		return null;
	}
}
