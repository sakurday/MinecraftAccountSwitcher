package com.accountswitcher.gui;

import com.accountswitcher.account.AccountStore;
import com.accountswitcher.account.AuthServer;
import com.accountswitcher.auth.YggdrasilAuth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/** Custom auth server dialog: enter a URL, probe its Yggdrasil metadata, cache it locally. */
public class AuthServerEditScreen extends Screen {
    private static final int BOX_WIDTH = 240;

    private final Screen parent;
    private EditBox urlBox;
    private Button confirmButton;

    private String status = "";
    private int statusColor = 0xFFAAAAAA;
    private CompletableFuture<AuthServer> pending;

    public AuthServerEditScreen(Screen parent) {
        super(Component.translatable("accountswitcher.server.custom.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BOX_WIDTH / 2;
        urlBox = new EditBox(this.font, x, 44, BOX_WIDTH, 20, Component.translatable("accountswitcher.url"));
        urlBox.setMaxLength(512);
        urlBox.setHint(Component.translatable("accountswitcher.url.hint"));
        this.addRenderableWidget(urlBox);

        confirmButton = Button.builder(Component.translatable("accountswitcher.confirm"), b -> startProbe())
                .bounds(this.width / 2 - 104, 76, 100, 20).build();
        this.addRenderableWidget(confirmButton);
        this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.cancel"), b -> onClose())
                .bounds(this.width / 2 + 4, 76, 100, 20).build());
    }

    private void startProbe() {
        if (pending != null) {
            return;
        }
        statusColor = 0xFFAAAAAA;
        status = Component.translatable("accountswitcher.probing").getString();
        confirmButton.active = false;
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                return YggdrasilAuth.probeServer(urlBox.getValue());
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (pending == null || !pending.isDone()) {
            return;
        }
        try {
            AuthServer server = pending.join();
            AccountStore.get().addServer(server);
            this.minecraft.setScreen(parent);
        } catch (Exception e) {
            pending = null;
            confirmButton.active = true;
            statusColor = 0xFFFF5555;
            status = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
        }
    }

    // Preprocessed to extractRenderState(GuiGraphicsExtractor, ...) on 26.x via mapping-26.1.2-1.21.11.txt
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, this.width / 2, 104, statusColor);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
