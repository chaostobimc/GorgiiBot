package com.gorgiibot;

import com.gorgiibot.gui.GiveawayScreen;
import com.gorgiibot.gui.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-Einstiegspunkt des GorgiiBot-Giveaway-Mods.
 *
 * <p>Registriert Taste (G), Client-Command (/giveaway) und Tick-Handler.
 * Der Mod ist rein clientseitig und braucht keinen Server-Mod.
 */
public class GorgiiBotClient implements ClientModInitializer {
    public static final String MOD_ID = "gorgiibot";

    public static KeyBinding openKey;
    public static GiveawayState state;

    @Override
    public void onInitializeClient() {
        state = new GiveawayState();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.gorgiibot.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.gorgiibot"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            state.tick();
            while (openKey.wasPressed()) {
                handleOpenKey(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("giveaway").executes(context -> {
                    MinecraftClient client = context.getSource().getClient();
                    client.setScreen(new GiveawayScreen());
                    return 1;
                })));
    }

    private void handleOpenKey(MinecraftClient client) {
        if (client.currentScreen == null) {
            client.setScreen(new GiveawayScreen());
        } else if (client.currentScreen instanceof GiveawayScreen screen
                && !(screen.getFocused() instanceof TextFieldWidget)) {
            client.setScreen(null);
        } else if (client.currentScreen instanceof SettingsScreen screen
                && !(screen.getFocused() instanceof TextFieldWidget)) {
            screen.saveAndClose();
        }
    }
}
