package dev.mcextra;

import dev.mcextra.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.resources.Identifier;

public class McExtraClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModConfig.load();
		// Свои бинды намеренно НЕ регистрируются как KeyMapping,
		// поэтому в стандартном меню «Управление» их нет.
		ClientTickEvents.START_CLIENT_TICK.register(BindHandler::tick);
		ClientTickEvents.START_CLIENT_TICK.register(ClutchHandler::onStart);
		ClientTickEvents.END_CLIENT_TICK.register(ClutchHandler::onEnd);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath("moreculling_extra", "sword_hitbox"),
			(graphics, tickCounter) -> SwordHitbox.render(graphics, tickCounter.getGameTimeDeltaPartialTick(true)));
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(ArrowPreviewRenderer::render);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(PearlPreviewRenderer::render);
	}
}
