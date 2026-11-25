package dev.borderhaze.client.glitch;

import dev.borderhaze.BorderHaze;
import dev.borderhaze.glitch.GlitchStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Set;
import java.util.UUID;

/**
 * Client entry point of the GLITCH mechanic: receives the server's state and forgets it on disconnect.
 * Independent of the BorderHaze client code.
 */
public final class GlitchClient {
	private GlitchClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(GlitchStatePayload.TYPE, (payload, context) -> {
			Set<UUID> players = payload.decode();
			context.client().execute(() -> {
				GlitchClientState.set(players);
				BorderHaze.LOGGER.info("[Border Haze][glitch] server state received: {} glitched player(s) {}", players.size(), players);
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GlitchClientState.clear());
	}
}
