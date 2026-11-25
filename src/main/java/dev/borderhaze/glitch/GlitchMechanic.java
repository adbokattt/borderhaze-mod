package dev.borderhaze.glitch;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Entry point of the GLITCH mechanic (common side). It is independent of BorderHaze: it shares only the project
 * infrastructure (AdminPermission, the networking and lifecycle setup) and has its own commands, state and file.
 */
public final class GlitchMechanic {
	private GlitchMechanic() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(GlitchStatePayload.TYPE, GlitchStatePayload.CODEC);
		GlitchManager.init();
		GlitchCommands.register();
	}
}
