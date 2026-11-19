package dev.borderhaze.client.border;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.debug.RenderDiagnostics;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Step 3 verification helper: logs the border state whenever it changes
 * (checked once per second). Lets us confirm that center/size/movement are read correctly.
 * Disabled with -Dborderhaze.debug=false
 */
public final class BorderDebug {
	private static int ticks = 0;
	private static String lastKey = "";

	private BorderDebug() {
	}

	public static void tick(Minecraft mc) {
		if (!RenderDiagnostics.enabled()) {
			return;
		}
		if (++ticks % 20 != 0) {
			return;
		}
		BorderSnapshot s = BorderTracker.read(mc);
		if (!s.valid()) {
			lastKey = "";
			return;
		}
		String key = String.format(Locale.ROOT, "center=(%.2f, %.2f) size=%.3f X[%.3f .. %.3f] Z[%.3f .. %.3f] infinite=%s",
				s.centerX(), s.centerZ(), s.size(), s.minX(), s.maxX(), s.minZ(), s.maxZ(), s.isEffectivelyInfinite());
		if (key.equals(lastKey)) {
			return;
		}
		lastKey = key;
		String player = "n/a";
		if (mc.player != null) {
			player = String.format(Locale.ROOT, "%.2f", s.signedDistance(mc.player.getX(), mc.player.getZ()));
		}
		BorderHaze.LOGGER.info("[Border Haze][border] {} | playerDistToEdge={}", key, player);
	}
}
