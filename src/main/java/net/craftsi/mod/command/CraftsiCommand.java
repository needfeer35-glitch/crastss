package net.craftsi.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.craftsi.mod.gui.CraftsiScreen;
import net.minecraft.client.MinecraftClient;

public class CraftsiCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("craftsi")
                .executes(context -> {
                    MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().setScreen(new CraftsiScreen())
                    );
                    return 1;
                })
        );
    }
}
