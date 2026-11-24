package dev.borderhaze.client.config;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.settings.BorderHazeSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client settings. The static fields below are the EFFECTIVE values that all rendering and sound code reads.
 *
 * Normally they come from the local file config/borderhaze.properties (re-read about once a second when it changes;
 * edited in game with /bhlocal). While the player is connected to a dedicated server that has Border Haze, the server's
 * settings replace them (applyServer) and the local values are kept aside untouched; on disconnect they come back
 * (clearServer). A player can opt out of server settings with ignoreServerSettings=true.
 *
 * Two values are always local and never sent by a server: debugMask and ignoreServerSettings.
 */
public final class HazeConfig {
	private HazeConfig() {
	}

	// ---- effective values (mirror BorderHazeSettings) ----
	public static volatile boolean enabled = true;
	public static volatile int style = 0;
	public static volatile float falloff = 3.0f;
	public static volatile float blurRadius = 18.0f;
	public static volatile float rippleAmplitude = 0.006f;
	public static volatile float rippleSpeed = 1.2f;
	public static volatile float rippleScale = 40.0f;
	public static volatile float pixelSize = 8.0f;
	public static volatile float pixelBlur = 0.6f;
	public static volatile float hazeStrength = 0.1f;
	public static volatile int hazeColor = 0xC8D4E4;
	public static volatile float edgeBlur = 64.0f;
	public static volatile float edgeDistance = 16.0f;
	public static volatile float edgeHaze = 0.45f;
	public static volatile boolean soundEnabled = true;
	public static volatile float soundVolume = 0.5f;
	public static volatile float soundDistance = 24.0f;

	// ---- always local ----
	/** DEBUG view: red tint outside the border + yellow line exactly on the border. */
	public static volatile boolean debugMask = false;
	/** true = ignore the settings pushed by servers and always use the local values. */
	public static volatile boolean ignoreServerSettings = false;

	private static volatile boolean serverControlled = false;
	/** The local values, kept here while a server's settings are active. */
	private static BorderHazeSettings savedLocal;

	private static Path file;
	private static long lastModified = -1;
	private static long lastCheck = 0;

	public static boolean isServerControlled() {
		return serverControlled;
	}

	// ------------------------------------------------------------------ server override

	/** The server pushed its settings (on join or after an admin changed something). */
	public static void applyServer(BorderHazeSettings s) {
		if (ignoreServerSettings) {
			return;
		}
		if (!serverControlled) {
			savedLocal = snapshot();
			serverControlled = true;
			BorderHaze.LOGGER.info("[Border Haze] this server controls the border visuals: using the server's settings");
		}
		apply(s);
	}

	/** Disconnected from the server: back to the local settings. */
	public static void clearServer() {
		if (!serverControlled) {
			return;
		}
		serverControlled = false;
		if (savedLocal != null) {
			apply(savedLocal);
			savedLocal = null;
		}
		BorderHaze.LOGGER.info("[Border Haze] left the server: local settings restored");
	}

	private static void apply(BorderHazeSettings s) {
		enabled = s.enabled;
		style = s.style;
		falloff = s.falloff;
		blurRadius = s.blurRadius;
		rippleAmplitude = s.rippleAmplitude;
		rippleSpeed = s.rippleSpeed;
		rippleScale = s.rippleScale;
		pixelSize = s.pixelSize;
		pixelBlur = s.pixelBlur;
		hazeStrength = s.hazeStrength;
		hazeColor = s.hazeColor;
		edgeBlur = s.edgeBlur;
		edgeDistance = s.edgeDistance;
		edgeHaze = s.edgeHaze;
		soundEnabled = s.soundEnabled;
		soundVolume = s.soundVolume;
		soundDistance = s.soundDistance;
	}

	private static BorderHazeSettings snapshot() {
		BorderHazeSettings s = new BorderHazeSettings();
		s.enabled = enabled;
		s.style = style;
		s.falloff = falloff;
		s.blurRadius = blurRadius;
		s.rippleAmplitude = rippleAmplitude;
		s.rippleSpeed = rippleSpeed;
		s.rippleScale = rippleScale;
		s.pixelSize = pixelSize;
		s.pixelBlur = pixelBlur;
		s.hazeStrength = hazeStrength;
		s.hazeColor = hazeColor;
		s.edgeBlur = edgeBlur;
		s.edgeDistance = edgeDistance;
		s.edgeHaze = edgeHaze;
		s.soundEnabled = soundEnabled;
		s.soundVolume = soundVolume;
		s.soundDistance = soundDistance;
		return s;
	}

	// ------------------------------------------------------------------ local file

	public static void load() {
		try {
			file = FabricLoader.getInstance().getConfigDir().resolve("borderhaze.properties");
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, fileText(new BorderHazeSettings(), false, false));
				BorderHaze.LOGGER.info("[Border Haze] created default config {}", file);
			}
			read();
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] config init failed, using defaults: {}", t.toString());
		}
	}

	/** Re-reads the file right now (used by /bhlocal reload). */
	public static void reload() {
		try {
			if (file != null) {
				read();
			}
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] config reload failed: {}", t.toString());
		}
	}

	/** Writes the LOCAL values (never the server's) to the config file. */
	public static void save() {
		try {
			if (file != null) {
				BorderHazeSettings local = serverControlled && savedLocal != null ? savedLocal : snapshot();
				Files.writeString(file, fileText(local, debugMask, ignoreServerSettings));
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

		debugMask = BorderHazeSettings.parseBool(p, "debugMask", false);
		ignoreServerSettings = BorderHazeSettings.parseBool(p, "ignoreServerSettings", false);
		BorderHazeSettings local = BorderHazeSettings.fromProperties(p);

		if (serverControlled) {
			if (ignoreServerSettings) {
				savedLocal = local;
				clearServer();
			} else {
				savedLocal = local; // keep the local copy fresh; the server's values stay active
			}
		} else {
			apply(local);
		}
		BorderHaze.LOGGER.info("[Border Haze] config loaded: enabled={} debugMask={} style={} blur={} haze={} edge={}@{} sound={}/{}@{} ignoreServer={}{}",
				local.enabled, debugMask, local.style == 1 ? "pixel" : "blur", local.blurRadius, local.hazeStrength,
				local.edgeBlur, local.edgeDistance, local.soundEnabled, local.soundVolume, local.soundDistance,
				ignoreServerSettings, serverControlled ? " (server settings are active right now)" : "");
	}

	private static String fileText(BorderHazeSettings s, boolean debug, boolean ignoreServer) {
		return s.toConfigText() + """

				# --- client only (a server can never change these) ---
				# true = DEBUG view: red tint outside the border + yellow line exactly on the border
				debugMask=%s
				# true = ignore the settings pushed by a server that has Border Haze, always use the values above
				ignoreServerSettings=%s
				""".formatted(debug, ignoreServer);
	}
}
