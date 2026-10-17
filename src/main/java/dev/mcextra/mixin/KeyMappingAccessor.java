package dev.mcextra.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к счётчику нажатий ванильного действия, чтобы «нажимать» его от имени своего бинда. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int mce$getClickCount();

	@Accessor("clickCount")
	void mce$setClickCount(int value);
}
