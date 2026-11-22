package dev.borderhaze.client.config;

import dev.borderhaze.BorderHaze;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Tiny text config: <game dir>/config/borderhaze.properties
 * It is re-read automatically (about once a second) when the file changes, so the look of the effect
 * can be tuned while the game is running. The /borderhaze command edits the same values and saves them here.
 */
public final class HazeConfig {
	private HazeConfig() {
	}

	/** Master switch. When off the mod is invisible: the vanilla border and warning are shown again. */
	public static volatile boolean enabled = true;
	/** true = DEBUG view: red tint outside the border + yellow line exactly on the border. */
	public static volatile boolean debugMask = false;
	/** Look of the effect: 0 = blur + ripple (heat haze), 1 = pixelated (mosaic) with a slight blur. */
	public static volatile int style = 0;
	/** Blocks over which the effect fades in, starting exactly at the border. */
	public static volatile float falloff = 3.0f;
	/** Blur style: maximum blur radius in screen pixels. */
	public static volatile float blurRadius = 18.0f;
	/** Blur style: ripple strength in screen UV units (0.004 = 0.4% of the screen). */
	public static volatile float rippleAmplitude = 0.006f;
	/** Blur style: ripple animation speed. */
	public static volatile float rippleSpeed = 1.2f;
	/** Blur style: ripple wave density. */
	public static volatile float rippleScale = 40.0f;
	/** Pixel style: size of one mosaic block in screen pixels. */
	public static volatile float pixelSize = 8.0f;
	/** Pixel style: softness. 0 = sharp blocks, 1 = each block averages a 2x larger area (slightly blurred). */
	public static volatile float pixelBlur = 0.6f;
	/** Both styles: how strongly the area beyond the border is washed towards the haze colour (0 = off, 1 = solid). */
	public static volatile float hazeStrength = 0.1f;
	/** Both styles: haze colour as 0xRRGGBB. */
	public static volatile int hazeColor = 0xC8D4E4;
	/** Blur at the screen edges when the player is near the border (replaces the vanilla red warning). Pixels at the very edge. 0 = off. */
	public static volatile float edgeBlur = 24.0f;
	/** Distance to the border (blocks) at which the edge blur starts to appear. */
	public static volatile float edgeDistance = 12.0f;
	/** Play the border hum when the player is near the border. */
	public static volatile boolean soundEnabled = true;
	/** Maximum volume of the hum (0 .. 1). It is also scaled by the game's "Ambient" volume slider. */
	public static volatile float soundVolume = 0.5f;
	/** Distance to the border (blocks) at which the hum starts to be heard. */
	public static volatile float soundDistance = 24.0f;

	private static Path file;
	private static long lastModified = -1;
	private static long lastCheck = 0;

	public static void load() {
		try {
			file = FabricLoader.getInstance().getConfigDir().resolve("borderhaze.properties");
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, render());
				BorderHaze.LOGGER.info("[Border Haze] created default config {}", file);
			}
			read();
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] config init failed, using defaults: {}", t.toString());
		}
	}

	/** Re-reads the file right now (used by /borderhaze reload). */
	public static void reload() {
		try {
			if (file != null) {
				read();
			}
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] config reload failed: {}", t.toString());
		}
	}

	/** Writes the current values (with comments) to the config file. Used by the /borderhaze command. */
	public static void save() {
		try {
			if (file != null) {
				Files.writeString(file, render());
				lastModified = Files.getLastModifiedTime(file).toMillis();
			}
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] could not save config: {}", t.toString());
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
		debugMask = bool(p, "debugMask", false);
		String st = p.getProperty("style", "blur").trim().toLowerCase(Locale.ROOT);
		style = (st.equals("pixel") || st.equals("pixelated") || st.equals("1")) ? 1 : 0;
		falloff = num(p, "falloff", 3.0f, 0.5f, 200f);
		blurRadius = num(p, "blurRadius", 18.0f, 0f, 96f);
		rippleAmplitude = num(p, "rippleAmplitude", 0.006f, 0f, 0.05f);
		rippleSpeed = num(p, "rippleSpeed", 1.2f, 0f, 20f);
		rippleScale = num(p, "rippleScale", 40.0f, 1f, 400f);
		pixelSize = num(p, "pixelSize", 8.0f, 2f, 64f);
		pixelBlur = num(p, "pixelBlur", 0.6f, 0f, 2f);
		hazeStrength = num(p, "hazeStrength", 0.1f, 0f, 1f);
		hazeColor = color(p, "hazeColor", 0xC8D4E4);
		edgeBlur = num(p, "edgeBlur", 24.0f, 0f, 96f);
		edgeDistance = num(p, "edgeDistance", 12.0f, 1f, 200f);
		soundEnabled = bool(p, "sound", true);
		soundVolume = num(p, "soundVolume", 0.5f, 0f, 1f);
		soundDistance = num(p, "soundDistance", 24.0f, 2f, 200f);
		BorderHaze.LOGGER.info("[Border Haze] config loaded: enabled={} debugMask={} style={} falloff={} blur={} haze={} #{} edgeBlur={}@{} sound={}/{}@{} (ripple {}/{}/{}, pixel {}/{})",
				enabled, debugMask, style == 1 ? "pixel" : "blur", falloff, blurRadius, hazeStrength, String.format("%06X", hazeColor),
				edgeBlur, edgeDistance, soundEnabled, soundVolume, soundDistance,
				rippleAmplitude, rippleSpeed, rippleScale, pixelSize, pixelBlur);
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

	private static int color(Properties p, String key, int def) {
		String v = p.getProperty(key);
		if (v == null) {
			return def;
		}
		try {
			String hex = v.trim();
			if (hex.startsWith("#")) {
				hex = hex.substring(1);
			}
			return Integer.parseInt(hex, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/** The config file text for the CURRENT values. */
	private static String render() {
		return """
				# Border Haze settings. Save the file while the game runs: changes apply within a second.
				# The same values can be changed in game with /borderhaze (see /borderhaze status).

				# Master switch. false = the mod is invisible (vanilla border and warning are shown again)
				enabled=%s

				# true  = DEBUG view: red tint outside the border + yellow line exactly on the border
				debugMask=%s

				# Look of the effect:
				#   blur  = blurred + heat-haze ripple (uses blurRadius, ripple*)
				#   pixel = pixelated mosaic with a slight blur (uses pixelSize, pixelBlur)
				style=%s

				# Blocks over which the effect fades in, starting exactly at the border (0.5 .. 200)
				falloff=%s

				# --- style=blur ---
				# Maximum blur radius in screen pixels (0 .. 96). Bigger = harder to see what is beyond the border.
				blurRadius=%s
				# Ripple (heat-haze) strength in screen UV units, 0 = off (0 .. 0.05)
				rippleAmplitude=%s
				# Ripple animation speed (0 .. 20)
				rippleSpeed=%s
				# Ripple wave density (1 .. 400)
				rippleScale=%s

				# --- style=pixel ---
				# Size of one mosaic block in screen pixels (2 .. 64)
				pixelSize=%s
				# Softness of the blocks: 0 = sharp, 1 = very soft (0 .. 2)
				pixelBlur=%s

				# --- both styles ---
				# How strongly everything beyond the border is washed towards the haze colour (0 .. 1). 0 = off
				hazeStrength=%s
				# Haze colour, hex RRGGBB
				hazeColor=%s

				# --- screen-edge blur (replaces the vanilla red warning at the screen edges) ---
				# Blur radius at the very edge of the screen when you are near the border, in pixels (0 .. 96). 0 = off
				edgeBlur=%s
				# Distance to the border (blocks) at which the edge blur starts to appear (1 .. 200)
				edgeDistance=%s

				# --- border hum ---
				# Play a low hum when you are near the border
				sound=%s
				# Maximum volume (0 .. 1). It is also scaled by the "Ambient" volume slider of the game
				soundVolume=%s
				# Distance to the border (blocks) at which the hum starts to be heard (2 .. 200)
				soundDistance=%s
				""".formatted(enabled, debugMask, style == 1 ? "pixel" : "blur", falloff, blurRadius, rippleAmplitude,
				rippleSpeed, rippleScale, pixelSize, pixelBlur, hazeStrength, String.format("%06X", hazeColor),
				edgeBlur, edgeDistance, soundEnabled, soundVolume, soundDistance);
	}
}
