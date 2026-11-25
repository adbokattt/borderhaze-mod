package dev.borderhaze.glitch;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Server-side events of the GLITCH mechanic, for other mechanics and for Vanish integrations. */
public final class GlitchEvents {
	private GlitchEvents() {
	}

	/** Fired on the server thread after GLITCH was switched on or off for a player (state already saved and sent to clients). */
	public static final Event<Changed> CHANGED = EventFactory.createArrayBacked(Changed.class,
			listeners -> (server, player, active) -> {
				for (Changed listener : listeners) {
					listener.onChanged(server, player, active);
				}
			});

	@FunctionalInterface
	public interface Changed {
		void onChanged(MinecraftServer server, UUID player, boolean active);
	}
}
