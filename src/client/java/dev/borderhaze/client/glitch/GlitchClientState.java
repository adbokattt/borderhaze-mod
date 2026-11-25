package dev.borderhaze.client.glitch;

import java.util.Set;
import java.util.UUID;

/**
 * What this client currently knows about GLITCH: the set of player UUIDs that are glitched, as last sent by the server.
 * Rendering code asks isGlitched(uuid) per player every frame, so it keeps working across respawns, dimension
 * changes and entity re-creation (the state belongs to the UUID, not to an entity object).
 */
public final class GlitchClientState {
	private static volatile Set<UUID> glitched = Set.of();

	private GlitchClientState() {
	}

	public static boolean isGlitched(UUID player) {
		return glitched.contains(player);
	}

	public static Set<UUID> all() {
		return glitched;
	}

	public static void set(Set<UUID> players) {
		glitched = Set.copyOf(players);
	}

	public static void clear() {
		glitched = Set.of();
	}
}
