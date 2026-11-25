package dev.borderhaze.glitch;

import dev.borderhaze.BorderHaze;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server -> client: the COMPLETE set of players that are GLITCH right now (UUIDs separated by ';').
 * A full snapshot (not a delta) is sent on every change and on join: it is tiny, and it makes the client state
 * self-healing (a missed packet can never leave a stale figure on somebody's screen).
 */
public record GlitchStatePayload(String text) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "glitch_state");

	public static final CustomPacketPayload.Type<GlitchStatePayload> TYPE = new CustomPacketPayload.Type<>(ID);

	public static final StreamCodec<RegistryFriendlyByteBuf, GlitchStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			GlitchStatePayload::text,
			GlitchStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static GlitchStatePayload of(Collection<UUID> players) {
		StringBuilder sb = new StringBuilder();
		for (UUID id : players) {
			if (sb.length() > 0) {
				sb.append(';');
			}
			sb.append(id);
		}
		return new GlitchStatePayload(sb.toString());
	}

	/** Invalid entries are skipped, so a malformed packet can never break the client. */
	public Set<UUID> decode() {
		Set<UUID> result = new HashSet<>();
		for (String part : text.split(";")) {
			String trimmed = part.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			try {
				result.add(UUID.fromString(trimmed));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return result;
	}
}
