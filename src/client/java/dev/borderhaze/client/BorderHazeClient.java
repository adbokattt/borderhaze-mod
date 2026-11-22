package dev.borderhaze.client;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.border.BorderDebug;
import dev.borderhaze.client.config.HazeConfig;
import dev.borderhaze.client.debug.RenderDiagnostics;
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

	public static boolean isVanillaBorderHidden() {
		return hideVanillaBorder;
	}

	public static void setVanillaBorderHidden(boolean hidden) {
		hideVanillaBorder = hidden;
	}

	public static boolean isEffectEnabled() {
		return effectEnabled;
	}

	@Override
	public void onInitializeClient() {
		BorderHaze.LOGGER.info("[Border Haze] client init OK (step 4b: depth mask + per-frame uniforms)");
		HazeConfig.load();
		ClientTickEvents.END_CLIENT_TICK.register(BorderDebug::tick);
		RenderDiagnostics.dump();
		ClientLifecycleEvents.CLIENT_STARTED.register(RenderDiagnostics::dumpResources);
	}
}
