package dev.mcextra.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

/** Клавиша клавиатуры или кнопка мыши. */
public class BindInput {
	public boolean mouse;
	public int code;

	public BindInput() {
	}

	public BindInput(boolean mouse, int code) {
		this.mouse = mouse;
		this.code = code;
	}

	public Component displayName() {
		InputConstants.Type type = mouse ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
		return type.getOrCreate(code).getDisplayName();
	}
}
