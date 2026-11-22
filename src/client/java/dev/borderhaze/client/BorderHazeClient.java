package dev.borderhaze.client;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.border.BorderDebug;
import dev.borderhaze.client.command.BorderHazeCommand;
import dev.borderhaze.client.config.HazeConfig;
import dev.borderhaze.client.debug.RenderDiagnostics;
import dev.borderhaze.client.sound.BorderSound;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client-only entrypoint.
 * Step 2: hides the vanilla world border visuals (WorldBorderRendererMixin).
 * Step 3: reads the synced border state (BorderTracker) and logs it.
 * Step 4b: post-effect with depth mask and per-frame uniforms (LevelRendererMixin, BorderEffect, BorderUniforms).
 */
public class BorderHazeClient implements ClientModInitializer {

	/** Hide vanilla border visuals. Will become a config option in a later step. */
	private static volatile boolean hideVanillaBorder = true;

	/** Master switch for the post effect. Disable with the JVM argument -Dborderhaze.effect=false */
	private static volatile boolean effectEnabled = !"false".equalsIgnoreCase(System.getProperty("borderhaze.effect", "true"));

	/** The vanilla border and its red warning are hidden only while the mod is switched on (/borderhaze off = pure vanilla). */
	public static boolean isVanillaBorderHidden() {
		return hideVanillaBorder && HazeConfig.enabled;
	}

	public static void setVanillaBorderHidden(boolean hidden) {
		hideVanillaBorder = hidden;
	}

	public static boolean isEffectEnabled() {
		return effectEnabled;
	}

	@Override
	public void onInitializeClient() {
		BorderHaze.LOGGER.info("[Border Haze] client init OK (2-pass blur / pixel styles, /borderhaze command)");
		HazeConfig.load();
		BorderHazeCommand.register();
		ClientTickEvents.END_CLIENT_TICK.register(BorderDebug::tick);
		ClientTickEvents.END_CLIENT_TICK.register(BorderSound::tick);
		RenderDiagnostics.dump();
		ClientLifecycleEvents.CLIENT_STARTED.register(RenderDiagnostics::dumpResources);
	}
}
