package dev.borderhaze.client.border;

/**
 * Immutable copy of the vanilla world border state at one moment.
 * All distances are in blocks. The border is an axis-aligned square in the XZ plane.
 */
public record BorderSnapshot(boolean valid,
							 double centerX, double centerZ, double size,
							 double minX, double maxX, double minZ, double maxZ) {

	public static final BorderSnapshot NONE = new BorderSnapshot(false, 0, 0, 0, 0, 0, 0, 0);

	/** The vanilla default border is ~5.99e7 blocks wide, which is effectively "no border". */
	public static final double EFFECTIVELY_INFINITE_SIZE = 1.0E7;

	public boolean isEffectivelyInfinite() {
		return !valid || size >= EFFECTIVELY_INFINITE_SIZE;
	}

	/**
	 * Signed distance from a point to the nearest border edge:
	 * positive inside the border, zero on the edge, negative outside.
	 * (Same metric the shader will use, so CPU and GPU agree.)
	 */
	public double signedDistance(double x, double z) {
		return Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
	}

	/**
	 * Cheap CPU-side test: can the haze be visible at all from this camera position?
	 * True if the camera is outside the border, or an edge is within the view range.
	 * When false, the whole post-effect pass is skipped (zero GPU cost).
	 */
	public boolean isRelevantFor(double camX, double camZ, double viewDistanceBlocks) {
		if (isEffectivelyInfinite()) {
			return false;
		}
		return signedDistance(camX, camZ) <= viewDistanceBlocks;
	}
}
