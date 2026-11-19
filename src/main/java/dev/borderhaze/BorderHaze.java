package dev.borderhaze;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint (runs on both the client and the dedicated server).
 * Later steps add the optional server-side sync/config.
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
	}
}
