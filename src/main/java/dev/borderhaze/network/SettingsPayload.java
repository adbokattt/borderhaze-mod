package dev.borderhaze.network;

import dev.borderhaze.BorderHaze;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: the border settings of the server, as compact "key=value" text
 * (see BorderHazeSettings#serialize). Text keeps the packet tolerant: unknown or missing keys just fall back to defaults,
 * and every value is clamped on arrival, so a malformed packet can never put the client into an invalid state.
 */
public record SettingsPayload(String text) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "settings");

	public static final CustomPacketPayload.Type<SettingsPayload> TYPE = new CustomPacketPayload.Type<>(ID);

	public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8,
			SettingsPayload::text,
			SettingsPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
