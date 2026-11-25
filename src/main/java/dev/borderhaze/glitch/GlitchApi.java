package dev.borderhaze.glitch;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Public, stable entry point of GLITCH for other code (a future Vanish system, other mechanics of this mod, other mods).
 *
 * VANISH INTEGRATION
 * GLITCH has priority over Vanish. A Vanish implementation must ask {@link #forcesVisibility(ServerPlayer)} wherever it decides
 * whether a player may be seen (tracking, tab list, entity packets) and must treat "true" as "do not hide this player".
 * It must NOT switch Vanish off: when GLITCH ends, forcesVisibility() turns false again and the original Vanish state applies.
 * The vanilla spectator-based vanish (a spectator is not tracked by non-spectators) is already handled by a built-in hook
 * (see mixin.ServerPlayerMixin), so spectator-style Vanish needs no extra work. Vanish systems that remove players from
 * tracking on their own (custom packets/mixins) call this method, and may listen to {@link GlitchEvents#CHANGED}
 * to re-evaluate the tracking when GLITCH starts or stops.
 */
public final class GlitchApi {
	private GlitchApi() {
	}

	public static boolean isGlitched(UUID player) {
		return GlitchManager.isGlitched(player);
	}

	/** true = this player must be visible to everybody right now, regardless of any Vanish state. */
	public static boolean forcesVisibility(ServerPlayer player) {
		return GlitchManager.isGlitched(player.getUUID());
	}

	/** A copy of the UUIDs of all players that are GLITCH right now. */
	public static Set<UUID> activePlayers() {
		return GlitchManager.snapshot();
	}
}
