package net.craftsi.mod;

import net.craftsi.mod.command.CraftsiCommand;
import net.craftsi.mod.logic.CraftExecutor;
import net.craftsi.mod.node.NodeGraph;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CraftsiMod implements ClientModInitializer {

    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.craftsi.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.craftsi"
        ));

        ClientCommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess) ->
                CraftsiCommand.register(dispatcher)
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.wasPressed()) {
                MinecraftClient.getInstance().send(() ->
                    MinecraftClient.getInstance().setScreen(
                        new net.craftsi.mod.gui.CraftsiScreen())
                );
            }
            if (client.player != null
                && client.world != null
                && client.world.isClient()) {
                CraftExecutor.tick(NodeGraph.getInstance(), client.player);
            }
        });
    }
}
