package dev.borderhaze.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.borderhaze.settings.BorderHazeSettings;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The SERVER command /borderhaze. Requires permission level 2 (gamemaster): operators, command blocks,
 * datapack functions and the console can use it; ordinary players (level 0) cannot even see it.
 *
 * Every change is saved to config/borderhaze-server.properties and pushed to all connected players immediately;
 * players who join later receive the current settings on join. Works only on a dedicated server
 * (in single player use /bhlocal or the local config).
 *
 *   /borderhaze                      status
 *   /borderhaze on | off             switch the whole effect for everybody (off = the vanilla border comes back)
 *   /borderhaze style blur|pixel
 *   /borderhaze blur <0..96>         /borderhaze haze <0..1>        /borderhaze hazecolor <RRGGBB>
 *   /borderhaze falloff <0.5..200>   /borderhaze ripple <0..0.05>   /borderhaze ripplespeed <0..20>
 *   /borderhaze ripplescale <1..400> /borderhaze pixelsize <2..64>  /borderhaze pixelblur <0..2>
 *   /borderhaze edge <0..192>        /borderhaze edgedistance <1..200>   /borderhaze edgehaze <0..1>
 *   /borderhaze sound <0..1>         /borderhaze sounddistance <2..200>
 *   /borderhaze reload               re-read the server file and push it to everybody
 *   /borderhaze reset                restore the defaults
 */
public final class ServerCommands {
	private static final String NOT_DEDICATED =
			"Border Haze: this command controls dedicated servers. In single player / LAN use /bhlocal or edit config/borderhaze.properties.";

	private ServerCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("borderhaze")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.executes(ctx -> status(ctx.getSource()))
						.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
						.then(Commands.literal("on").executes(ctx -> change(ctx.getSource(), s -> s.enabled = true, "effect ON for everybody")))
						.then(Commands.literal("off").executes(ctx -> change(ctx.getSource(), s -> s.enabled = false, "effect OFF for everybody (vanilla border)")))
						.then(Commands.literal("style")
								.then(Commands.literal("blur").executes(ctx -> change(ctx.getSource(), s -> s.style = 0, "style = blur")))
								.then(Commands.literal("pixel").executes(ctx -> change(ctx.getSource(), s -> s.style = 1, "style = pixel"))))
						.then(floatNode("blur", "radius", 0f, 96f, (s, v) -> s.blurRadius = v))
						.then(floatNode("haze", "strength", 0f, 1f, (s, v) -> s.hazeStrength = v))
						.then(Commands.literal("hazecolor")
								.then(Commands.argument("rrggbb", StringArgumentType.word()).executes(ctx -> {
									String text = StringArgumentType.getString(ctx, "rrggbb");
									int rgb = BorderHazeSettings.parseHex(text, -1);
									if (rgb < 0) {
										ctx.getSource().sendFailure(Component.literal("Border Haze: '" + text + "' is not a colour. Use 6 hex digits, e.g. C8D4E4"));
										return 0;
									}
									return change(ctx.getSource(), s -> s.hazeColor = rgb, "haze colour = " + String.format("%06X", rgb));
								})))
						.then(floatNode("falloff", "blocks", 0.5f, 200f, (s, v) -> s.falloff = v))
						.then(floatNode("ripple", "amplitude", 0f, 0.05f, (s, v) -> s.rippleAmplitude = v))
						.then(floatNode("ripplespeed", "speed", 0f, 20f, (s, v) -> s.rippleSpeed = v))
						.then(floatNode("ripplescale", "scale", 1f, 400f, (s, v) -> s.rippleScale = v))
						.then(floatNode("pixelsize", "pixels", 2f, 64f, (s, v) -> s.pixelSize = v))
						.then(floatNode("pixelblur", "softness", 0f, 2f, (s, v) -> s.pixelBlur = v))
						.then(floatNode("edge", "pixels", 0f, 192f, (s, v) -> s.edgeBlur = v))
						.then(floatNode("edgedistance", "blocks", 1f, 200f, (s, v) -> s.edgeDistance = v))
						.then(floatNode("edgehaze", "strength", 0f, 1f, (s, v) -> s.edgeHaze = v))
						.then(floatNode("sound", "volume", 0f, 1f, (s, v) -> {
							s.soundVolume = v;
							s.soundEnabled = v > 0.0f;
						}))
						.then(floatNode("sounddistance", "blocks", 2f, 200f, (s, v) -> s.soundDistance = v))
						.then(Commands.literal("reload").executes(ctx -> {
							MinecraftServer server = ctx.getSource().getServer();
							if (!server.isDedicatedServer()) {
								ctx.getSource().sendFailure(Component.literal(NOT_DEDICATED));
								return 0;
							}
							BorderHazeServer.reload();
							BorderHazeServer.broadcast(server);
							ctx.getSource().sendSuccess(() -> Component.literal("Border Haze: server config reloaded and sent to all players"), false);
							return Command.SINGLE_SUCCESS;
						}))
						.then(Commands.literal("reset").executes(ctx -> change(ctx.getSource(), s -> s.copyFrom(new BorderHazeSettings()), "all settings reset to defaults")))
		));
	}

	/** literal -> float argument -> change. */
	private static LiteralArgumentBuilder<CommandSourceStack> floatNode(String literal, String argument, float min, float max,
																		 BiConsumer<BorderHazeSettings, Float> setter) {
		return Commands.literal(literal).then(Commands.argument(argument, FloatArgumentType.floatArg(min, max)).executes(ctx -> {
			float value = FloatArgumentType.getFloat(ctx, argument);
			return change(ctx.getSource(), s -> setter.accept(s, value), literal + " = " + value);
		}));
	}

	/** Applies a change to the server settings, saves the file and pushes the result to every player. */
	private static int change(CommandSourceStack source, Consumer<BorderHazeSettings> mutation, String message) {
		MinecraftServer server = source.getServer();
		if (!server.isDedicatedServer()) {
			source.sendFailure(Component.literal(NOT_DEDICATED));
			return 0;
		}
		BorderHazeSettings settings = BorderHazeServer.settings();
		mutation.accept(settings);
		settings.clamp();
		BorderHazeServer.save();
		BorderHazeServer.broadcast(server);
		source.sendSuccess(() -> Component.literal("Border Haze: " + message), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int status(CommandSourceStack source) {
		BorderHazeSettings s = BorderHazeServer.settings();
		String where = source.getServer().isDedicatedServer() ? "" : " (single player: not pushed, use /bhlocal)";
		source.sendSuccess(() -> Component.literal("Border Haze server settings" + where + ": "
				+ (s.enabled ? "ON" : "OFF")
				+ " | style=" + (s.style == 1 ? "pixel" : "blur")
				+ " | blur=" + s.blurRadius + " | haze=" + s.hazeStrength + " #" + String.format("%06X", s.hazeColor)
				+ " | falloff=" + s.falloff
				+ " | ripple=" + s.rippleAmplitude + "/" + s.rippleSpeed + "/" + s.rippleScale
				+ " | pixel=" + s.pixelSize + "/" + s.pixelBlur
				+ " | edge=" + s.edgeBlur + "@" + s.edgeDistance + " haze " + s.edgeHaze
				+ " | sound=" + (s.soundEnabled ? s.soundVolume : 0.0f) + "@" + s.soundDistance), false);
		return Command.SINGLE_SUCCESS;
	}
}
