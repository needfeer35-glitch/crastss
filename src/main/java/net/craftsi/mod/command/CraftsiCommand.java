package net.craftsi.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.craftsi.mod.gui.CraftsiScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class CraftsiCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("craftsi")
                .executes(context -> {
                    net.minecraft.client.MinecraftClient.getInstance().setScreen(new CraftsiScreen());
                    return 1;
                })
        );
    }
}
