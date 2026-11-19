package dev.borderhaze.client;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.debug.RenderDiagnostics;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint.
 * Step 2: hides the vanilla world border visuals (see WorldBorderRendererMixin).
 * Later steps: read border state, register the blur/ripple post-effect.
 */
public class BorderHazeClient implements ClientModInitializer {

	/** Hide vanilla border visuals. Will become a config option in a later step. */
	private static volatile boolean hideVanillaBorder = true;

	public static boolean isVanillaBorderHidden() {
		return hideVanillaBorder;
	}

	public static void setVanillaBorderHidden(boolean hidden) {
		hideVanillaBorder = hidden;
	}

	@Override
	public void onInitializeClient() {
		BorderHaze.LOGGER.info("[Border Haze] client init OK (step 2: vanilla border hidden)");
		RenderDiagnostics.dump();
	}
}
