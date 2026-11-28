package dev.borderhaze.client.glitch.render;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.borderhaze.BorderHaze;
import dev.borderhaze.client.glitch.GlitchClientState;
import dev.borderhaze.client.glitch.GlitchTimeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The GLITCH "shell", version 4. The exact silhouette comes from the real (black) player model on the picture; the pose is also
 * described ANALYTICALLY (six capsules: head, torso, two arms, two legs, following the body yaw, the walking animation and
 * sneaking) and projected to the screen, but only as a HINT: it tells the post effect where to look, so dark background and
 * distance/fog cannot be confused with the figure. The post effect builds the ragged, wobbling edge, the purple / blue / cyan
 * fringes and the glowing eyes.
 *
 * Cost: when nobody is glitched, or no glitched figure is on screen, this returns after a few checks and nothing is sent
 * to the GPU. Safety: any problem disables the effect for the session (logged once).
 */
public final class GlitchEffect {
	private static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "glitch");
	private static final double MAX_DISTANCE = 128.0;
	private static final float EYE_SIZE = 0.0375f;     // blocks: radius of the bright core of an eye
	private static final float RECT_MARGIN = 0.03f;    // uv: extra room around the figure rectangle
	private static final double CLOUD_REACH = 0.5;     // blocks: room around the body for the ragged edge and the colour fringes

	private static boolean failed = false;
	private static boolean loggedFirstRun = false;
	private static volatile boolean debugMask = false;

	/** Debug view: paints the analytic figure (core magenta, cloud limit cyan) so its alignment with the model can be checked. */
	public static void setDebugMask(boolean on) {
		debugMask = on;
	}

	public static boolean isDebugMask() {
		return debugMask;
	}

	private GlitchEffect() {
	}

	public static void render(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, CameraRenderState cam, Matrix4fc modelView) {
		if (failed) {
			return;
		}
		try {
			Set<UUID> glitched = GlitchClientState.all();
			if (glitched.isEmpty()) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			ClientLevel level = mc.level;
			if (level == null) {
				return;
			}

			float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
			boolean firstPerson = mc.options.getCameraType().isFirstPerson();
			Matrix4f proj = cam.projectionMatrix;
			long nowMs = System.currentTimeMillis();

			List<Player> players = new ArrayList<>();
			for (Player p : level.players()) {
				if (!glitched.contains(p.getUUID())) {
					continue;
				}
				if (firstPerson && p == mc.player) {
					continue; // you do not see your own body in first person
				}
				if (p.getPosition(partial).distanceToSqr(cam.pos) <= MAX_DISTANCE * MAX_DISTANCE) {
					players.add(p);
				}
			}
			if (players.isEmpty()) {
				return;
			}
			players.sort(Comparator.comparingDouble(p -> p.getPosition(partial).distanceToSqr(cam.pos)));

			List<GlitchFigure> figures = new ArrayList<>();
			for (Player p : players) {
				if (figures.size() >= GlitchUniforms.MAX_FIGURES) {
					break;
				}
				GlitchFigure figure = project(p, partial, cam.pos, modelView, proj, nowMs);
				if (figure != null) {
					figures.add(figure);
				}
			}
			if (figures.isEmpty()) {
				return;
			}

			PostChain chain = mc.getShaderManager().getPostChain(CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) {
				disable("post chain '" + CHAIN_ID + "' could not be loaded (look for shader/resource errors earlier in the log)", null);
				return;
			}
			boolean reverseZ = Math.abs(proj.m22()) < 0.01f;
			float near = reverseZ ? proj.m32() : 0.05f;
			GlitchUniforms.update(chain, reverseZ, debugMask, near, proj.m11(), figures);
			chain.process(mc.gameRenderer.mainRenderTarget(), allocator);

			if (!loggedFirstRun) {
				loggedFirstRun = true;
				BorderHaze.LOGGER.info("[Border Haze][glitch] glitch chain '{}' executed successfully (first run, {} figure(s))", CHAIN_ID, figures.size());
			}
		} catch (Throwable t) {
			disable("exception while running the glitch chain", t);
		}
	}

	/** Returns null when the figure is not on screen. */
	private static GlitchFigure project(Player p, float partial, Vec3 camPos, Matrix4fc view, Matrix4f proj, long nowMs) {
		Vec3 pos = p.getPosition(partial);
		UUID id = p.getUUID();
		GlitchFigure figure = new GlitchFigure();
		float[] out = new float[4];

		// ---------------- screen rectangle (body bounding box + room for the cloud) ----------------
		AABB box = p.getBoundingBox().move(pos.subtract(p.position())).inflate(CLOUD_REACH);
		float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
		boolean anyBehind = false;
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? box.minX : box.maxX;
			double y = (i & 2) == 0 ? box.minY : box.maxY;
			double z = (i & 4) == 0 ? box.minZ : box.maxZ;
			if (!projectPoint(view, proj, camPos, x, y, z, out)) {
				anyBehind = true;
				break;
			}
			minU = Math.min(minU, out[0]);
			maxU = Math.max(maxU, out[0]);
			minV = Math.min(minV, out[1]);
			maxV = Math.max(maxV, out[1]);
		}
		if (anyBehind) {
			minU = 0.0f;
			minV = 0.0f;
			maxU = 1.0f;
			maxV = 1.0f;
		} else {
			minU = Math.max(0.0f, minU - RECT_MARGIN);
			minV = Math.max(0.0f, minV - RECT_MARGIN);
			maxU = Math.min(1.0f, maxU + RECT_MARGIN);
			maxV = Math.min(1.0f, maxV + RECT_MARGIN);
			if (minU >= maxU || minV >= maxV) {
				return null;
			}
		}
		figure.rect[0] = minU;
		figure.rect[1] = minV;
		figure.rect[2] = maxU;
		figure.rect[3] = maxV;

		// ---------------- pose ----------------
		double yawRad = Math.toRadians(p.getViewYRot(partial));
		Vec3 fwd = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
		Vec3 side = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));

		float swingPos = p.walkAnimation.position(partial);
		float swingAmt = Math.min(1.0f, p.walkAnimation.speed(partial));
		float a = swingPos * 0.6662f;
		double rLeg = Math.cos(a) * 1.4 * swingAmt;
		double lLeg = Math.cos(a + Math.PI) * 1.4 * swingAmt;
		double rArm = Math.cos(a + Math.PI) * swingAmt;
		double lArm = Math.cos(a) * swingAmt;
		boolean crouch = p.isCrouching();
		double drop = crouch ? 0.22 : 0.0;
		double lean = crouch ? 0.18 : 0.0;

		int variant = GlitchTimeline.variant(id, nowMs);

		// capsule endpoints in world space: head, torso, right arm, left arm, right leg, left leg
		Vec3[][] ends = new Vec3[GlitchFigure.PRIMS][2];
		double[] radius = {0.30, 0.26, 0.14, 0.14, 0.14, 0.14};
		Vec3 headC = at(pos, side, fwd, 0.0, 1.66 - drop, lean);
		ends[0][0] = headC;
		ends[0][1] = headC;
		ends[1][0] = at(pos, side, fwd, 0.0, 1.38 - drop, lean);
		ends[1][1] = at(pos, side, fwd, 0.0, 0.78, 0.0);
		ends[2][0] = at(pos, side, fwd, -0.34, 1.36 - drop, lean);
		ends[2][1] = limbEnd(ends[2][0], fwd, rArm, 0.62);
		ends[3][0] = at(pos, side, fwd, 0.34, 1.36 - drop, lean);
		ends[3][1] = limbEnd(ends[3][0], fwd, lArm, 0.62);
		ends[4][0] = at(pos, side, fwd, -0.12, 0.70, 0.0);
		ends[4][1] = limbEnd(ends[4][0], fwd, rLeg, 0.68);
		ends[5][0] = at(pos, side, fwd, 0.12, 0.70, 0.0);
		ends[5][1] = limbEnd(ends[5][0], fwd, lLeg, 0.68);

		boolean[] missing = new boolean[GlitchFigure.PRIMS];
		missing[0] = variant == GlitchTimeline.VARIANT_HEAD || variant == GlitchTimeline.VARIANT_ALL;
		missing[1] = variant == GlitchTimeline.VARIANT_BODY || variant == GlitchTimeline.VARIANT_ALL;
		missing[2] = variant == GlitchTimeline.VARIANT_RIGHT_ARM || variant == GlitchTimeline.VARIANT_ALL;
		missing[3] = variant == GlitchTimeline.VARIANT_LEFT_ARM || variant == GlitchTimeline.VARIANT_ALL;
		missing[4] = variant == GlitchTimeline.VARIANT_RIGHT_LEG || variant == GlitchTimeline.VARIANT_ALL;
		missing[5] = variant == GlitchTimeline.VARIANT_LEFT_LEG || variant == GlitchTimeline.VARIANT_ALL;

		for (int k = 0; k < GlitchFigure.PRIMS; k++) {
			float[] pa = new float[4];
			float[] pb = new float[4];
			boolean ok = !missing[k]
					&& projectPoint(view, proj, camPos, ends[k][0].x, ends[k][0].y, ends[k][0].z, pa)
					&& projectPoint(view, proj, camPos, ends[k][1].x, ends[k][1].y, ends[k][1].z, pb);
			if (ok) {
				figure.prims[k][0] = pa[0];
				figure.prims[k][1] = pa[1];
				figure.prims[k][2] = pb[0];
				figure.prims[k][3] = pb[1];
				figure.radii[k] = 0.5f * proj.m11() * (float) radius[k] / (0.5f * (pa[3] + pb[3]));
			} else {
				figure.radii[k] = -1.0f;
			}
		}
		figure.radii[6] = -1.0f;
		figure.radii[7] = -1.0f;

		// view depth of the figure centre (used to hide the cloud behind walls) and a per-figure seed
		if (!projectPoint(view, proj, camPos, pos.x, pos.y + 1.0, pos.z, out)) {
			return null;
		}
		figure.info[0] = out[3];
		figure.info[1] = (float) ((id.getLeastSignificantBits() >>> 11) & 0xFFFF) / 65535.0f;

		// ---------------- eyes ----------------
		float level = GlitchTimeline.headPresent(variant) ? GlitchTimeline.eyeLevel(id, nowMs) : 0.0f;
		Vec3 eye = p.getEyePosition(partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0e-6) {
			right = new Vec3(1.0, 0.0, 0.0);
		}
		right = right.normalize();
		Vec3 toCamera = camPos.subtract(eye).normalize();
		float facing = (float) Math.max(0.0, Math.min(1.0, (look.dot(toCamera) - 0.05) / 0.45));

		for (int s = 0; s < 2; s++) {
			double sign = s == 0 ? 1.0 : -1.0;
			Vec3 e = eye.add(look.scale(0.27)).add(right.scale(0.115 * sign)).add(0.0, -0.05, 0.0);
			if (projectPoint(view, proj, camPos, e.x, e.y, e.z, out)) {
				figure.eyes[s][0] = out[0];
				figure.eyes[s][1] = out[1];
				figure.eyes[s][2] = out[2];
				figure.eyes[s][3] = 0.5f * proj.m11() * EYE_SIZE / out[3];
				figure.eyeLevel[s] = level * facing;
			} else {
				figure.eyeLevel[s] = 0.0f;
			}
		}
		return figure;
	}

	/** A point in the player's local frame: s to the side, y up, f forward, relative to the feet position. */
	private static Vec3 at(Vec3 pos, Vec3 side, Vec3 fwd, double s, double y, double f) {
		return new Vec3(pos.x + side.x * s + fwd.x * f, pos.y + y, pos.z + side.z * s + fwd.z * f);
	}

	/** The free end of a limb of the given length that hangs from {@code top} and swings forward/back by {@code angle} radians. */
	private static Vec3 limbEnd(Vec3 top, Vec3 fwd, double angle, double length) {
		double forward = Math.sin(angle) * length;
		return new Vec3(top.x + fwd.x * forward, top.y - Math.cos(angle) * length, top.z + fwd.z * forward);
	}

	/** world point -> {u, v, depth, w}; false when the point is behind the camera. Depth is NDC z (= stored depth in 26.2). */
	private static boolean projectPoint(Matrix4fc view, Matrix4f proj, Vec3 camPos, double x, double y, double z, float[] out) {
		Vector4f v = new Vector4f((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z), 1.0f);
		view.transform(v);
		proj.transform(v);
		if (v.w < 0.05f) {
			return false;
		}
		out[0] = v.x / v.w * 0.5f + 0.5f;
		out[1] = v.y / v.w * 0.5f + 0.5f;
		out[2] = v.z / v.w;
		out[3] = v.w;
		return true;
	}

	private static void disable(String reason, Throwable t) {
		failed = true;
		if (t != null) {
			BorderHaze.LOGGER.error("[Border Haze][glitch] glitch effect DISABLED for this session: {}", reason, t);
		} else {
			BorderHaze.LOGGER.error("[Border Haze][glitch] glitch effect DISABLED for this session: {}", reason);
		}
	}
}
