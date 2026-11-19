package dev.borderhaze.client;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.border.BorderDebug;
import dev.borderhaze.client.debug.RenderDiagnostics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client-only entrypoint.
 * Step 2: hides the vanilla world border visuals (see WorldBorderRendererMixin).
 * Step 3: reads the synced border state (BorderTracker) and logs it for verification.
 * Next: the blur/ripple post-effect.
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
		BorderHaze.LOGGER.info("[Border Haze] client init OK (step 3: border state tracking)");
		ClientTickEvents.END_CLIENT_TICK.register(BorderDebug::tick);
		RenderDiagnostics.dump();
	}
}
