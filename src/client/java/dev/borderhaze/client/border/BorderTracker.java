package dev.borderhaze.client.border;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Reads the vanilla world border of the current client level.
 * The server already syncs center/size/lerp to every client (vanilla packets),
 * so no custom networking is needed: we only read the client-side WorldBorder object.
 */
public final class BorderTracker {
	private BorderTracker() {
	}

	/** Builds a fresh snapshot. Cheap (a few field reads), safe to call every frame. */
	public static BorderSnapshot read(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			return BorderSnapshot.NONE;
		}
		WorldBorder border = level.getWorldBorder();
		return new BorderSnapshot(
				true,
				border.getCenterX(), border.getCenterZ(), border.getSize(),
				border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ());
	}
}
