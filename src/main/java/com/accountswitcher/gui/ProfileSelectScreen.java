package com.accountswitcher.gui;

import com.accountswitcher.SessionController;
import com.accountswitcher.account.Account;
import com.accountswitcher.account.AccountStore;
import com.accountswitcher.account.AuthServer;
import com.accountswitcher.auth.YggdrasilAuth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Profile selection after successful Yggdrasil authentication (when the account has multiple roles). */
public class ProfileSelectScreen extends Screen {
    private final Screen parent;
    private final AuthServer server;
    private final String username;
    /** The just-verified password, kept in memory only and cached per launch. */
    private final String password;
    private final YggdrasilAuth.AuthResult result;

    public ProfileSelectScreen(Screen parent, AuthServer server, String username,
                               String password, YggdrasilAuth.AuthResult result) {
        super(Component.translatable("accountswitcher.chooseProfile"));
        this.parent = parent;
        this.server = server;
        this.username = username;
        this.password = password;
        this.result = result;
    }

    @Override
    protected void init() {
        int y = 36;
        int x = this.width / 2 - 110;
        for (YggdrasilAuth.Profile profile : result.availableProfiles) {
            this.addRenderableWidget(Button.builder(Component.literal(profile.name), b -> finish(profile))
                    .bounds(x, y, 220, 20).build());
            y += 24;
        }
        this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.cancel"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 32, 100, 20).build());
    }

    private void finish(YggdrasilAuth.Profile profile) {
        Account account = new Account();
        account.type = Account.Type.YGGDRASIL;
        account.authServer = server.url;
        account.username = username;
        account.profileId = profile.id;
        account.profileName = profile.name;
        SessionController.applyYggdrasil(account, result.accessToken);
        SessionController.cachePassword(account.key(), password);
        AccountStore.get().put(account);
        AccountStore.get().setActive(account.key());
        // A completed login leaves the whole flow, not back to the login screen
        if (parent instanceof YggdrasilLoginScreen login) {
            login.closeAfterLogin();
        } else {
            this.minecraft.setScreen(parent);
        }
    }

    // Preprocessed to extractRenderState(GuiGraphicsExtractor, ...) on 26.x via mapping-26.1.2-1.21.11.txt
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
