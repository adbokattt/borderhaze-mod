package dev.borderhaze.server;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.network.SettingsPayload;
import dev.borderhaze.settings.BorderHazeSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server-side state of Border Haze (dedicated servers only).
 *
 *  - The authoritative settings live in config/borderhaze-server.properties (created with defaults on first start).
 *  - Every change (command or /borderhaze reload) is saved to that file and pushed to ALL connected players.
 *  - Every player who joins (or rejoins) gets the current settings right away.
 *  - Players without the mod are never sent anything (canSend check), so vanilla clients are unaffected.
 *
 * In single player / LAN nothing is pushed: the player's own config/borderhaze.properties applies there.
 */
public final class BorderHazeServer {
	private static final BorderHazeSettings SETTINGS = new BorderHazeSettings();
	private static Path file;

	private BorderHazeServer() {
	}

	public static BorderHazeSettings settings() {
		return SETTINGS;
	}

	/** Registers the lifecycle and join hooks. Called once from the common initializer. */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (server.isDedicatedServer()) {
				load();
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (server.isDedicatedServer() && ServerPlayNetworking.canSend(handler, SettingsPayload.TYPE)) {
				sender.sendPacket(new SettingsPayload(SETTINGS.serialize()));
			}
		});
	}

	/** Loads the server config, creating it with defaults when it does not exist yet. */
	public static void load() {
		try {
			file = FabricLoader.getInstance().getConfigDir().resolve("borderhaze-server.properties");
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, new BorderHazeSettings().toConfigText());
				BorderHaze.LOGGER.info("[Border Haze] created default server config {}", file);
			}
			readFile();
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] could not load the server config, using defaults: {}", t.toString());
		}
	}

	/** Re-reads the file (after a manual edit). */
	public static void reload() {
		try {
			if (file != null) {
				readFile();
			}
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] could not reload the server config: {}", t.toString());
		}
	}

	/** Writes the current settings to the server config file. */
	public static void save() {
		try {
			if (file != null) {
				Files.writeString(file, SETTINGS.toConfigText());
			}
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze] could not save the server config: {}", t.toString());
		}
	}

	/** Sends the current settings to every online player who has the mod. */
	public static void broadcast(MinecraftServer server) {
		SettingsPayload payload = new SettingsPayload(SETTINGS.serialize());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, SettingsPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void readFile() throws java.io.IOException {
		Properties p = new Properties();
		try (java.io.Reader reader = Files.newBufferedReader(file)) {
			p.load(reader);
		}
		SETTINGS.copyFrom(BorderHazeSettings.fromProperties(p));
		BorderHaze.LOGGER.info("[Border Haze] server settings loaded: enabled={} style={} blur={} haze={} edge={}@{} sound={}",
				SETTINGS.enabled, SETTINGS.style == 1 ? "pixel" : "blur", SETTINGS.blurRadius, SETTINGS.hazeStrength,
				SETTINGS.edgeBlur, SETTINGS.edgeDistance, SETTINGS.soundEnabled ? SETTINGS.soundVolume : 0.0f);
	}
}
