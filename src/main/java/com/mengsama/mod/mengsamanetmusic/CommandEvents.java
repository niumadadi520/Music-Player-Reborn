package com.mengsama.mod.mengsamanetmusic;

import com.mengsama.mod.mengsamanetmusic.config.ConfigManager;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mengsamanetmusic")
public final class CommandEvents {
    private CommandEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mengsamanetmusic")
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(CommandEvents::reload))
                        .then(Commands.literal("cache")
                                .then(Commands.argument("id", LongArgumentType.longArg())
                                        .executes(CommandEvents::cache)))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        ConfigManager.reload();
        context.getSource().sendSuccess(() -> Component.literal("MengSamaNetMusic config reloaded."), false);
        return 1;
    }

    private static int cache(CommandContext<CommandSourceStack> context) {
        long id = LongArgumentType.getLong(context, "id");
        context.getSource().sendSuccess(() -> Component.literal("Caching music ID: " + id), false);
        return 1;
    }
}
