package com.accountswitcher.gui;

import com.accountswitcher.SessionController;
import com.accountswitcher.account.Account;
import com.accountswitcher.account.AccountStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Offline account dialog: player name only, multiple accounts allowed; also edits an existing one. */
public class OfflineAddScreen extends Screen {
    private static final int BOX_WIDTH = 220;

    private final Screen parent;
    /** Non-null when renaming an existing offline account. */
    private final Account editing;
    private EditBox nameBox;

    public OfflineAddScreen(Screen parent) {
        this(parent, null);
    }

    public OfflineAddScreen(Screen parent, Account editing) {
        super(Component.translatable("accountswitcher.offline.title"));
        this.parent = parent;
        this.editing = editing;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BOX_WIDTH / 2;
        nameBox = new EditBox(this.font, x, 44, BOX_WIDTH, 20, Component.translatable("accountswitcher.name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("accountswitcher.name"));
        if (editing != null) {
            nameBox.setValue(editing.username);
        }
        this.addRenderableWidget(nameBox);

        this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.confirm"), b -> confirm())
                .bounds(this.width / 2 - 104, 76, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.cancel"), b -> onClose())
                .bounds(this.width / 2 + 4, 76, 100, 20).build());
    }

    private void confirm() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        Account account = new Account();
        account.type = Account.Type.OFFLINE;
        account.username = name;
        if (editing != null && !editing.key().equals(account.key())) {
            AccountStore.get().remove(editing.key());
        }
        SessionController.applyOffline(name);
        AccountStore.get().put(account);
        AccountStore.get().setActive(account.key());
        this.minecraft.setScreen(parent);
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
