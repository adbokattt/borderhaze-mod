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
 * The GLITCH "shell". The body is the vanilla player model drawn in "marker black"; this class runs a post effect over
 * the finished picture that finds those marker pixels and turns them into the unstable figure: ragged wobbling edge,
 * purple / blue / cyan colour fringes, ghost copies, slice displacement, static, and the two glowing eyes (which are drawn by
 * the effect around their projected 3D positions, so they glow and bloom like light and are not painted on a skin).
 *
 * Cost: when nobody is glitched, or no glitched figure is on screen, this returns after a few checks and nothing is sent
 * to the GPU. Safety: any problem disables the effect for the session (logged once); the figure then stays a plain black silhouette.
 */
public final class GlitchEffect {
	private static final Identifier CHAIN_ID = Identifier.fromNamespaceAndPath(BorderHaze.MOD_ID, "glitch");
	private static final double MAX_DISTANCE = 128.0;
	private static final float EYE_SIZE = 0.075f;   // blocks: radius of the bright core of an eye
	private static final float RECT_MARGIN = 0.035f; // uv: room around the figure for fringes and jitter

	private static boolean failed = false;
	private static boolean loggedFirstRun = false;

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

			// 1) which glitched players are near enough
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

			// 2) project each figure (rectangle + eyes) to the screen
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

			// 3) run the post effect
			PostChain chain = mc.getShaderManager().getPostChain(CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
			if (chain == null) {
				disable("post chain '" + CHAIN_ID + "' could not be loaded (look for shader/resource errors earlier in the log)", null);
				return;
			}
			boolean reverseZ = Math.abs(proj.m22()) < 0.01f;
			GlitchUniforms.update(chain, reverseZ, figures);
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
		GlitchFigure figure = new GlitchFigure();

		// --- screen rectangle from the bounding box ---
		AABB box = p.getBoundingBox().move(pos.subtract(p.position())).inflate(0.35);
		float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
		boolean anyBehind = false;
		float[] out = new float[4];
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
			// part of the figure is behind the camera: be generous and allow the whole screen
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
				return null; // completely off screen
			}
		}
		figure.rect[0] = minU;
		figure.rect[1] = minV;
		figure.rect[2] = maxU;
		figure.rect[3] = maxV;

		// --- eyes: two points on the face, in front of the head, following the head's look direction ---
		UUID id = p.getUUID();
		int variant = GlitchTimeline.variant(id, nowMs);
		float level = GlitchTimeline.headPresent(variant) ? GlitchTimeline.eyeLevel(id, nowMs) : 0.0f;

		Vec3 eye = p.getEyePosition(partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0e-6) {
			right = new Vec3(1.0, 0.0, 0.0); // looking straight up or down
		}
		right = right.normalize();
		Vec3 toCamera = camPos.subtract(eye).normalize();
		float facing = (float) Math.max(0.0, Math.min(1.0, (look.dot(toCamera) - 0.05) / 0.45)); // eyes fade out when the figure looks away

		for (int side = 0; side < 2; side++) {
			double sign = side == 0 ? 1.0 : -1.0;
			Vec3 e = eye.add(look.scale(0.27)).add(right.scale(0.115 * sign)).add(0.0, -0.05, 0.0);
			if (projectPoint(view, proj, camPos, e.x, e.y, e.z, out)) {
				figure.eyes[side][0] = out[0];
				figure.eyes[side][1] = out[1];
				figure.eyes[side][2] = out[2];
				figure.eyes[side][3] = 0.5f * proj.m11() * EYE_SIZE / out[3]; // radius as a fraction of the screen height
				figure.eyeLevel[side] = level * facing;
			} else {
				figure.eyeLevel[side] = 0.0f;
			}
		}
		return figure;
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
