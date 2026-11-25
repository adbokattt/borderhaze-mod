package dev.borderhaze.glitch;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.borderhaze.BorderHaze;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side truth of the GLITCH mechanic.
 *
 *  - WHO: a set of player UUIDs (stable, unlike nicknames). Independent of the player entity, so it survives
 *    death, respawn, dimension changes, relogging and server restarts.
 *  - WHERE IT IS SAVED: <world folder>/borderhaze/glitch.json. Per world, written on every change and when the server stops,
 *    read when the server starts. (The BorderHaze settings are global server config; this is per-world per-player data,
 *    so it lives with the world. Same approach otherwise: a plain readable file, loaded at start, saved on change.)
 *  - SYNC: every change is pushed to all connected players that have the mod (full snapshot), and every player gets the
 *    snapshot when they join. Players without the mod are never sent anything.
 */
public final class GlitchManager {
	private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
	private static Path file;

	private GlitchManager() {
	}

	// ------------------------------------------------------------------ queries

	public static boolean isGlitched(UUID player) {
		return ACTIVE.contains(player);
	}

	public static Set<UUID> snapshot() {
		return Set.copyOf(ACTIVE);
	}

	// ------------------------------------------------------------------ changes

	/**
	 * Switches GLITCH on/off for a player: saves, syncs to every client, fires the event.
	 * @return true if the state actually changed
	 */
	public static boolean set(MinecraftServer server, UUID player, boolean active) {
		boolean changed = active ? ACTIVE.add(player) : ACTIVE.remove(player);
		if (changed) {
			save();
			broadcast(server);
			GlitchEvents.CHANGED.invoker().onChanged(server, player, active);
		}
		return changed;
	}

	// ------------------------------------------------------------------ lifecycle

	/** Registers the server hooks. Called once from the common initializer. */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(GlitchManager::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
		// the static state must not leak from one world to the next in the same JVM (single player)
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());

		// everybody who joins (or rejoins) receives the current state right away
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler, GlitchStatePayload.TYPE)) {
				sender.sendPacket(GlitchStatePayload.of(ACTIVE));
			}
		});
	}

	private static void broadcast(MinecraftServer server) {
		GlitchStatePayload payload = GlitchStatePayload.of(ACTIVE);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, GlitchStatePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	// ------------------------------------------------------------------ persistence

	private static void load(MinecraftServer server) {
		ACTIVE.clear();
		try {
			file = server.getWorldPath(LevelResource.ROOT).resolve("borderhaze").resolve("glitch.json");
			if (!Files.exists(file)) {
				return;
			}
			try (Reader reader = Files.newBufferedReader(file)) {
				JsonElement root = JsonParser.parseReader(reader);
				if (root.isJsonObject() && root.getAsJsonObject().has("players")) {
					for (JsonElement entry : root.getAsJsonObject().getAsJsonArray("players")) {
						try {
							ACTIVE.add(UUID.fromString(entry.getAsString()));
						} catch (RuntimeException badEntry) {
							BorderHaze.LOGGER.warn("[Border Haze][glitch] ignoring an invalid entry in {}: {}", file, entry);
						}
					}
				}
			}
			BorderHaze.LOGGER.info("[Border Haze][glitch] loaded {} glitched player(s) from {}", ACTIVE.size(), file);
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze][glitch] could not load {}: {}", file, t.toString());
		}
	}

	private static void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			JsonArray players = new JsonArray();
			ACTIVE.stream().map(UUID::toString).sorted().forEach(players::add);
			root.add("players", players);
			// write to a temp file first, then replace: a crash can never leave a half-written file
			Path tmp = file.resolveSibling("glitch.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
			}
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Throwable t) {
			BorderHaze.LOGGER.warn("[Border Haze][glitch] could not save {}: {}", file, t.toString());
		}
	}
}
