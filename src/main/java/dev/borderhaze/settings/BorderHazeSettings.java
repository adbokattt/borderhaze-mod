package dev.borderhaze.settings;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Properties;

/**
 * All look-and-sound parameters of the border effect in one place. Used by BOTH sides:
 *  - the dedicated server keeps one instance (config/borderhaze-server.properties) and pushes it to every client,
 *  - the client keeps its own local one (config/borderhaze.properties) and swaps to the server's while connected.
 * Pure data + text (de)serialisation, no Minecraft classes, so it is safe on a dedicated server.
 */
public final class BorderHazeSettings {
	/** Master switch. false = the mod is invisible (vanilla border and warning are shown again). */
	public boolean enabled = true;
	/** 0 = blur + ripple (heat haze), 1 = pixelated mosaic with a slight blur. */
	public int style = 0;
	/** Blocks over which the effect fades in, starting exactly at the border. */
	public float falloff = 3.0f;
	/** Blur style: maximum blur radius in screen pixels. */
	public float blurRadius = 18.0f;
	/** Blur style: ripple strength in screen UV units. */
	public float rippleAmplitude = 0.006f;
	/** Blur style: ripple animation speed. */
	public float rippleSpeed = 1.2f;
	/** Blur style: ripple wave density. */
	public float rippleScale = 40.0f;
	/** Pixel style: mosaic block size in screen pixels. */
	public float pixelSize = 8.0f;
	/** Pixel style: softness (0 = sharp blocks). */
	public float pixelBlur = 0.6f;
	/** Both styles: wash towards the haze colour beyond the border (0 = off). */
	public float hazeStrength = 0.1f;
	/** Both styles: haze colour 0xRRGGBB. */
	public int hazeColor = 0xC8D4E4;
	/** Screen blur near the border: max radius in pixels (0 = off). */
	public float edgeBlur = 64.0f;
	/** Distance (blocks) at which the screen blur starts. */
	public float edgeDistance = 16.0f;
	/** Haze wash over the fully blurred screen at the border (0 = off). */
	public float edgeHaze = 0.45f;
	/** Play the border hum. */
	public boolean soundEnabled = true;
	/** Hum volume 0..1 (also scaled by the game's Ambient slider). */
	public float soundVolume = 0.5f;
	/** Distance (blocks) at which the hum starts to be heard. */
	public float soundDistance = 24.0f;

	public BorderHazeSettings copy() {
		BorderHazeSettings c = new BorderHazeSettings();
		c.copyFrom(this);
		return c;
	}

	public void copyFrom(BorderHazeSettings o) {
		enabled = o.enabled;
		style = o.style;
		falloff = o.falloff;
		blurRadius = o.blurRadius;
		rippleAmplitude = o.rippleAmplitude;
		rippleSpeed = o.rippleSpeed;
		rippleScale = o.rippleScale;
		pixelSize = o.pixelSize;
		pixelBlur = o.pixelBlur;
		hazeStrength = o.hazeStrength;
		hazeColor = o.hazeColor;
		edgeBlur = o.edgeBlur;
		edgeDistance = o.edgeDistance;
		edgeHaze = o.edgeHaze;
		soundEnabled = o.soundEnabled;
		soundVolume = o.soundVolume;
		soundDistance = o.soundDistance;
	}

	/** Forces every value into its valid range (protects against hand-edited files and bad network data). */
	public void clamp() {
		style = style == 1 ? 1 : 0;
		falloff = c(falloff, 0.5f, 200f);
		blurRadius = c(blurRadius, 0f, 96f);
		rippleAmplitude = c(rippleAmplitude, 0f, 0.05f);
		rippleSpeed = c(rippleSpeed, 0f, 20f);
		rippleScale = c(rippleScale, 1f, 400f);
		pixelSize = c(pixelSize, 2f, 64f);
		pixelBlur = c(pixelBlur, 0f, 2f);
		hazeStrength = c(hazeStrength, 0f, 1f);
		hazeColor &= 0xFFFFFF;
		edgeBlur = c(edgeBlur, 0f, 192f);
		edgeDistance = c(edgeDistance, 1f, 200f);
		edgeHaze = c(edgeHaze, 0f, 1f);
		soundVolume = c(soundVolume, 0f, 1f);
		soundDistance = c(soundDistance, 2f, 200f);
	}

	private static float c(float v, float min, float max) {
		if (Float.isNaN(v)) {
			return min;
		}
		return Math.max(min, Math.min(max, v));
	}

	// ------------------------------------------------------------------ text <-> object

	public static BorderHazeSettings fromProperties(Properties p) {
		BorderHazeSettings s = new BorderHazeSettings();
		s.enabled = parseBool(p, "enabled", true);
		String st = p.getProperty("style", "blur").trim().toLowerCase(Locale.ROOT);
		s.style = (st.equals("pixel") || st.equals("pixelated") || st.equals("1")) ? 1 : 0;
		s.falloff = parseNum(p, "falloff", 3.0f);
		s.blurRadius = parseNum(p, "blurRadius", 18.0f);
		s.rippleAmplitude = parseNum(p, "rippleAmplitude", 0.006f);
		s.rippleSpeed = parseNum(p, "rippleSpeed", 1.2f);
		s.rippleScale = parseNum(p, "rippleScale", 40.0f);
		s.pixelSize = parseNum(p, "pixelSize", 8.0f);
		s.pixelBlur = parseNum(p, "pixelBlur", 0.6f);
		s.hazeStrength = parseNum(p, "hazeStrength", 0.1f);
		s.hazeColor = parseColor(p, "hazeColor", 0xC8D4E4);
		s.edgeBlur = parseNum(p, "edgeBlur", 64.0f);
		s.edgeDistance = parseNum(p, "edgeDistance", 16.0f);
		s.edgeHaze = parseNum(p, "edgeHaze", 0.45f);
		s.soundEnabled = parseBool(p, "sound", true);
		s.soundVolume = parseNum(p, "soundVolume", 0.5f);
		s.soundDistance = parseNum(p, "soundDistance", 24.0f);
		s.clamp();
		return s;
	}

	/** Parses the compact "key=value" text produced by {@link #serialize()} (or a whole config file). */
	public static BorderHazeSettings parse(String text) {
		Properties p = new Properties();
		try {
			p.load(new StringReader(text));
		} catch (IOException ignored) {
			// StringReader cannot fail; fall through to the defaults
		}
		return fromProperties(p);
	}

	/** Compact text for the network packet. */
	public String serialize() {
		return "enabled=" + enabled
				+ "\nstyle=" + (style == 1 ? "pixel" : "blur")
				+ "\nfalloff=" + falloff
				+ "\nblurRadius=" + blurRadius
				+ "\nrippleAmplitude=" + rippleAmplitude
				+ "\nrippleSpeed=" + rippleSpeed
				+ "\nrippleScale=" + rippleScale
				+ "\npixelSize=" + pixelSize
				+ "\npixelBlur=" + pixelBlur
				+ "\nhazeStrength=" + hazeStrength
				+ "\nhazeColor=" + String.format("%06X", hazeColor)
				+ "\nedgeBlur=" + edgeBlur
				+ "\nedgeDistance=" + edgeDistance
				+ "\nedgeHaze=" + edgeHaze
				+ "\nsound=" + soundEnabled
				+ "\nsoundVolume=" + soundVolume
				+ "\nsoundDistance=" + soundDistance
				+ "\n";
	}

	/** The commented config-file text for these values (used by the server file and by the client file). */
	public String toConfigText() {
		return """
				# Border Haze settings. Save the file while the game/server runs: changes apply within a second (client)
				# or after /borderhaze reload (server).

				# Master switch. false = the mod is invisible (vanilla border and warning are shown again)
				enabled=%s

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

				# --- screen blur near the border (replaces the vanilla red warning at the screen edges) ---
				# It blurs EVERYTHING on screen (world, hand, hotbar, open inventories). It creeps in from the screen edges as you
				# approach the border and covers the whole screen when you stand at the border.
				# Maximum blur radius in pixels (0 .. 192). 0 = off
				edgeBlur=%s
				# Distance to the border (blocks) at which the blur starts to appear (1 .. 200)
				edgeDistance=%s
				# Haze wash over the fully blurred screen at the border, 0 = off (0 .. 1)
				edgeHaze=%s

				# --- border hum ---
				# Play a low hum when you are near the border
				sound=%s
				# Maximum volume (0 .. 1). It is also scaled by the "Ambient" volume slider of the game
				soundVolume=%s
				# Distance to the border (blocks) at which the hum starts to be heard (2 .. 200)
				soundDistance=%s
				""".formatted(enabled, style == 1 ? "pixel" : "blur", falloff, blurRadius, rippleAmplitude,
				rippleSpeed, rippleScale, pixelSize, pixelBlur, hazeStrength, String.format("%06X", hazeColor),
				edgeBlur, edgeDistance, edgeHaze, soundEnabled, soundVolume, soundDistance);
	}

	// ------------------------------------------------------------------ parsing helpers

	public static boolean parseBool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		return v == null ? def : Boolean.parseBoolean(v.trim());
	}

	private static float parseNum(Properties p, String key, float def) {
		String v = p.getProperty(key);
		if (v == null) {
			return def;
		}
		try {
			return Float.parseFloat(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/** Hex "RRGGBB" or "#RRGGBB" to 0xRRGGBB; returns {@code def} when the text is not a valid colour. */
	public static int parseColor(Properties p, String key, int def) {
		String v = p.getProperty(key);
		return v == null ? def : parseHex(v, def);
	}

	public static int parseHex(String text, int def) {
		try {
			String hex = text.trim();
			if (hex.startsWith("#")) {
				hex = hex.substring(1);
			}
			if (hex.length() != 6) {
				return def;
			}
			return Integer.parseInt(hex, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
