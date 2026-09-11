package com.accountswitcher.mixin;

import com.accountswitcher.account.AccountStore;
import com.accountswitcher.gui.AccountManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the "Accounts" button to the multiplayer server list screen. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
    @Inject(method = "init", at = @At("RETURN"))
    private void accountswitcher_onInit(CallbackInfo ci) {
        JoinMultiplayerScreen self = (JoinMultiplayerScreen) (Object) this;
        AccountStore.UiConfig ui = AccountStore.get().ui;
        ((ScreenAccessor) self).accountswitcher_addRenderableWidget(
                Button.builder(Component.translatable("accountswitcher.openAccounts"),
                                button -> Minecraft.getInstance().setScreen(new AccountManagerScreen(self)))
                        .bounds(ui.openButtonX, ui.openButtonY, ui.openButtonWidth, ui.openButtonHeight)
                        .build());
    }
}
