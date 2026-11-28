package dev.borderhaze.client.glitch;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.borderhaze.client.glitch.render.GlitchEffect;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

/**
 * LOCAL client command /glitchlocal (nothing is sent to any server):
 *   /glitchlocal debug <true|false>   show which pixels the effect recognises as a GLITCH body (magenta) and the figure rectangles
 */
public final class GlitchClientCommand {
	private GlitchClientCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("glitchlocal")
						.then(ClientCommands.literal("debug")
								.then(ClientCommands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
									boolean on = BoolArgumentType.getBool(ctx, "enabled");
									GlitchEffect.setDebugMask(on);
									ctx.getSource().sendFeedback(Component.literal("GLITCH debug view = " + on));
									return Command.SINGLE_SUCCESS;
								})))
						.executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal("GLITCH debug view = " + GlitchEffect.isDebugMask()
									+ "  (use /glitchlocal debug true|false)"));
							return Command.SINGLE_SUCCESS;
						})
		));
	}
}
