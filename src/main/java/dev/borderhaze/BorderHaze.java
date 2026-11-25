package dev.borderhaze;

import dev.borderhaze.glitch.GlitchMechanic;
import dev.borderhaze.network.SettingsPayload;
import dev.borderhaze.server.BorderHazeServer;
import dev.borderhaze.server.ServerCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint (runs on the client AND on the dedicated server).
 *
 * Server side: the /borderhaze command (permission level 2), the persistent server config and the sync of the
 * settings to all players (on change and on join). Client side: see BorderHazeClient.
 */
public class BorderHaze implements ModInitializer {
	public static final String MOD_ID = "borderhaze";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		String mc = FabricLoader.getInstance().getModContainer("minecraft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		LOGGER.info("[Border Haze] common init OK (running on Minecraft {}, compiled against 26.2)", mc);

		// the packet must be registered on both sides before anything is sent or received
		PayloadTypeRegistry.clientboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);

		// ---- mechanic 1: BorderHaze (unchanged) ----
		BorderHazeServer.init();
		ServerCommands.register();

		// ---- mechanic 2: GLITCH (independent; shares only the project infrastructure) ----
		GlitchMechanic.init();
	}
}
