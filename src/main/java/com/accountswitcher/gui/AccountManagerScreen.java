package com.accountswitcher.gui;

import com.accountswitcher.AccountSwitcher;
import com.accountswitcher.SessionController;
import com.accountswitcher.account.Account;
import com.accountswitcher.account.AccountStore;
import com.accountswitcher.auth.YggdrasilAuth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Global account manager: lists stored accounts with their login source and ID,
 * activates the selected account, and offers add/edit/delete actions.
 */
public class AccountManagerScreen extends Screen {
    private static final int ROW_STEP = 22;
    private static final int ROW_WIDTH = 260;
    private static final int ACTION_WIDTH = 20;

    private final Screen parent;

    /** Bounds of the row for the currently active account, for the highlight border. */
    private int activeRowX = -1;
    private int activeRowY;
    private int activeRowW;

    /** Activation with the runtime-cached password: re-authenticate in the background, no screen. */
    private CompletableFuture<YggdrasilAuth.AuthResult> pendingAuth;
    private Account pendingAccount;

    private String status = "";
    private int statusColor = 0xFFAAAAAA;

    public AccountManagerScreen(Screen parent) {
        super(Component.translatable("accountswitcher.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        activeRowX = -1;
        AccountStore store = AccountStore.get();
        int x = this.width / 2 - ROW_WIDTH / 2;
        int y = 36;
        int rowWidth = ROW_WIDTH - ACTION_WIDTH * 2 - 4;

        // Premium row: the launcher session, single and implicit
        boolean premiumAvailable = SessionController.isPremiumSession();
        String premiumName = premiumAvailable && SessionController.originalUser() != null
                ? SessionController.originalUser().getName() : "";
        String premiumLabel = activeMark("premium") + sourceLabel(null)
                + (premiumName.isEmpty() ? "" : " " + premiumName);
        Button premiumButton = Button.builder(Component.literal(premiumLabel), b -> {
                    SessionController.applyPremium();
                    AccountStore.get().setActive("premium");
                    this.rebuildWidgets();
                })
                .bounds(x, y, ROW_WIDTH, 20)
                .build();
        premiumButton.active = premiumAvailable;
        if (!premiumAvailable) {
            premiumButton.setMessage(Component.translatable("accountswitcher.premiumUnavailable"));
        }
        this.addRenderableWidget(premiumButton);
        if (premiumAvailable && store.activeKey.equals("premium")) {
            activeRowX = x;
            activeRowY = y;
            activeRowW = ROW_WIDTH;
        }
        y += ROW_STEP;

        // Stored accounts: [account][edit][delete]
        int listBottom = this.height - 62;
        for (Account account : store.accounts) {
            if (account.type == Account.Type.PREMIUM) {
                continue;
            }
            if (y > listBottom) {
                break;
            }
            String label = activeMark(account.key()) + sourceLabel(account) + " " + displayName(account);
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> activate(account))
                    .bounds(x, y, rowWidth, 20).build());
            if (store.activeKey.equals(account.key())) {
                activeRowX = x;
                activeRowY = y;
                activeRowW = rowWidth;
            }
            this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.edit"),
                            b -> edit(account))
                    .bounds(x + ROW_WIDTH - ACTION_WIDTH * 2 - 2, y, ACTION_WIDTH, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.delete"), b -> {
                        AccountStore.get().remove(account.key());
                        this.rebuildWidgets();
                    })
                    .bounds(x + ROW_WIDTH - ACTION_WIDTH, y, ACTION_WIDTH, 20).build());
            y += ROW_STEP;
        }

        // Bottom actions
        int bottomY = this.height - 32;
        this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.addYggdrasil"),
                        b -> this.minecraft.setScreen(new YggdrasilLoginScreen(this, null)))
                .bounds(this.width / 2 - 130, bottomY, 125, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.addOffline"),
                        b -> this.minecraft.setScreen(new OfflineAddScreen(this)))
                .bounds(this.width / 2 + 5, bottomY, 125, 20).build());
    }

    /** Left click activates directly; a screen opens only when a password is required. */
    private void activate(Account account) {
        if (account.type != Account.Type.YGGDRASIL) {
            SessionController.applyOffline(account.username);
            AccountStore.get().setActive(account.key());
            this.rebuildWidgets();
            return;
        }
        // Same launch, already authenticated once: reuse the in-memory token, no network
        if (SessionController.applyCachedYggdrasil(account)) {
            AccountStore.get().setActive(account.key());
            this.rebuildWidgets();
            return;
        }
        String password = SessionController.cachedPassword(account.key());
        if (password == null) {
            // First switch in this client run: password required
            this.minecraft.setScreen(new YggdrasilLoginScreen(this, account));
            return;
        }
        if (pendingAuth != null) {
            return;
        }
        pendingAccount = account;
        statusColor = 0xFFAAAAAA;
        status = Component.translatable("accountswitcher.authenticating").getString();
        pendingAuth = CompletableFuture.supplyAsync(() -> {
            try {
                return YggdrasilAuth.authenticate(account.authServer, account.username, password);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        });
    }

    /** Edit: re-enter credentials for a Yggdrasil account, rename an offline one. */
    private void edit(Account account) {
        if (account.type == Account.Type.YGGDRASIL) {
            this.minecraft.setScreen(new YggdrasilLoginScreen(this, account));
        } else {
            this.minecraft.setScreen(new OfflineAddScreen(this, account));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingAuth == null || !pendingAuth.isDone()) {
            return;
        }
        YggdrasilAuth.AuthResult result;
        Account account = pendingAccount;
        pendingAuth = null;
        pendingAccount = null;
        try {
            result = pendingAuth.join();
        } catch (Exception e) {
            // Cached password rejected (changed, or server rate limit): ask again, show why
            String message = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
            AccountSwitcher.LOGGER.info("[AccountSwitcher] background re-auth failed for {}: {}",
                    account.key(), message);
            status = "";
            this.minecraft.setScreen(new YggdrasilLoginScreen(this, account, message));
            return;
        }
        YggdrasilAuth.Profile profile = YggdrasilAuth.pickProfile(result, account.profileId);
        if (profile == null) {
            statusColor = 0xFFFF5555;
            status = Component.translatable("accountswitcher.error.noProfile").getString();
            return;
        }
        account.profileId = profile.id;
        account.profileName = profile.name;
        SessionController.applyYggdrasil(account, result.accessToken);
        AccountStore.get().put(account);
        AccountStore.get().setActive(account.key());
        status = "";
        this.rebuildWidgets();
    }

    private String activeMark(String key) {
        return AccountStore.get().activeKey.equals(key) ? "\u2713 " : "";
    }

    private String sourceLabel(Account account) {
        if (account == null) {
            return "[" + Component.translatable("accountswitcher.source.premium").getString() + "]";
        }
        if (account.type == Account.Type.OFFLINE) {
            return "[" + Component.translatable("accountswitcher.source.offline").getString() + "]";
        }
        String serverName = AccountStore.get().serverByUrl(account.authServer)
                .map(s -> s.name).orElse(account.authServer);
        return "[" + serverName + "]";
    }

    private String displayName(Account account) {
        return account.type == Account.Type.OFFLINE ? account.username : account.profileName;
    }

    // Preprocessed to extractRenderState(GuiGraphicsExtractor, ...) on 26.x via mapping-26.1.2-1.21.11.txt
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (activeRowX >= 0) {
            // 1px gold border around the row of the active account
            int x2 = activeRowX + activeRowW;
            int y2 = activeRowY + 20;
            graphics.fill(activeRowX, activeRowY, x2, activeRowY + 1, 0xFFFFAA00);
            graphics.fill(activeRowX, y2 - 1, x2, y2, 0xFFFFAA00);
            graphics.fill(activeRowX, activeRowY, activeRowX + 1, y2, 0xFFFFAA00);
            graphics.fill(x2 - 1, activeRowY, x2, y2, 0xFFFFAA00);
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height - 46, statusColor);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
