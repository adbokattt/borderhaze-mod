package dev.borderhaze.client.config;

import dev.borderhaze.BorderHaze;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Tiny text config: <game dir>/config/borderhaze.properties
 * It is re-read automatically (about once a second) when the file changes, so the look of the effect
 * can be tuned while the game is running: edit the file, save, look at the screen.
 */
public final class HazeConfig {
	private HazeConfig() {
	}

	/** Master switch for the whole post effect. */
	public static volatile boolean enabled = true;
	/** true = DEBUG view: red tint outside the border + yellow line exactly on the border. false = real blur/ripple. */
	public static volatile boolean debugMask = true;
	/** Blocks over which the effect fades in, starting exactly at the border. */
	public static volatile float falloff = 3.0f;
	/** Maximum blur radius in screen pixels. */
	public static volatile float blurRadius = 22.0f;
	/** Ripple strength in screen UV units (0.004 = 0.4% of the screen). */
	public static volatile float rippleAmplitude = 0.006f;
	/** Ripple animation speed. */
	public static volatile float rippleSpeed = 1.2f;
	/** Ripple wave density. */
	public static volatile float rippleScale = 40.0f;

	private static Path file;
	private static long lastModified = -1;
	private static long lastCheck = 0;

	public static void load() {
		try {
			file = FabricLoader.getInstance().getConfigDir().resolve("borderhaze.properties");
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, defaultText());
				BorderHaze.LOGGER.info("[Border Haze] created default config {}", file);
			}
			read();
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] config init failed, using defaults: {}", t.toString());
		}
	}

	/** Cheap: checks the file timestamp at most once per second. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (file == null || now - lastCheck < 1000) {
			return;
		}
		lastCheck = now;
		try {
			long m = Files.getLastModifiedTime(file).toMillis();
			if (m != lastModified) {
				read();
			}
		} catch (IOException ignored) {
		}
	}

	private static void read() throws IOException {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(file)) {
			p.load(r);
		}
		lastModified = Files.getLastModifiedTime(file).toMillis();
		enabled = bool(p, "enabled", true);
		debugMask = bool(p, "debugMask", true);
		falloff = num(p, "falloff", 3.0f, 0.5f, 200f);
		blurRadius = num(p, "blurRadius", 22.0f, 0f, 64f);
		rippleAmplitude = num(p, "rippleAmplitude", 0.006f, 0f, 0.05f);
		rippleSpeed = num(p, "rippleSpeed", 1.2f, 0f, 20f);
		rippleScale = num(p, "rippleScale", 40.0f, 1f, 400f);
		BorderHaze.LOGGER.info("[Border Haze] config loaded: enabled={} debugMask={} falloff={} blurRadius={} rippleAmplitude={} rippleSpeed={} rippleScale={}",
				enabled, debugMask, falloff, blurRadius, rippleAmplitude, rippleSpeed, rippleScale);
	}

	private static boolean bool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		return v == null ? def : Boolean.parseBoolean(v.trim());
	}

	private static float num(Properties p, String key, float def, float min, float max) {
		String v = p.getProperty(key);
		if (v == null) {
			return def;
		}
		try {
			float f = Float.parseFloat(v.trim());
			return Math.max(min, Math.min(max, f));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static String defaultText() {
		return """
				# Border Haze settings. Save the file while the game runs: changes apply within a second.

				# Master switch for the whole effect
				enabled=true

				# true  = DEBUG view: red tint outside the border + yellow line exactly on the border
				# false = the real effect (blur + ripple)
				debugMask=true

				# Blocks over which the effect fades in, starting exactly at the border (0.5 .. 200)
				falloff=3.0

				# Maximum blur radius in screen pixels (0 .. 64)
				blurRadius=22.0

				# Ripple (heat-haze) strength in screen UV units, 0 = off (0 .. 0.05)
				rippleAmplitude=0.006

				# Ripple animation speed (0 .. 20)
				rippleSpeed=1.2

				# Ripple wave density (1 .. 400)
				rippleScale=40.0
				""";
	}
}
