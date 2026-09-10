package com.accountswitcher.gui;

import com.accountswitcher.SessionController;
import com.accountswitcher.account.Account;
import com.accountswitcher.account.AccountStore;
import com.accountswitcher.auth.YggdrasilAuth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Yggdrasil login: pick an auth server (custom dropdown incl. a "custom" entry
 * that opens the server editor), enter username/password and verify; then pick a
 * profile if the account has several.
 *
 * For an existing account only the password is asked (identity and the stored
 * profile choice are reused, no profile screen); the account manager also
 * re-authenticates silently with the runtime password cache. Passwords are
 * never stored in the config.
 */
public class YggdrasilLoginScreen extends Screen {
    private static final int BOX_WIDTH = 220;
    private static final int ROW_HEIGHT = 14;
    /** Sentinel dropdown value that opens the custom-server editor; untypeable as a real name. */
    private static final String CUSTOM_OPTION = "\u0000custom";

    private final Screen parent;
    /** Non-null when re-authenticating an existing account: server/username are prefilled. */
    private final Account existing;

    private List<com.accountswitcher.account.AuthServer> servers;
    private List<String> serverValues = List.of();
    private Button serverButton;
    private boolean serverListOpen;
    private String selectedServer = "";
    private EditBox usernameBox;
    private EditBox passwordBox;
    private Button loginButton;

    private String status = "";
    private int statusColor = 0xFFAAAAAA;
    private CompletableFuture<YggdrasilAuth.AuthResult> pending;
    private String pendingUsername = "";
    private String pendingPassword = "";
    private com.accountswitcher.account.AuthServer pendingServer;

    public YggdrasilLoginScreen(Screen parent, Account existing) {
        super(Component.translatable("accountswitcher.yggdrasil.title"));
        this.parent = parent;
        this.existing = existing;
    }

    /** Opens with an error banner, e.g. a failed background re-authentication. */
    public YggdrasilLoginScreen(Screen parent, Account existing, String initialError) {
        this(parent, existing);
        if (initialError != null && !initialError.isEmpty()) {
            this.status = initialError;
            this.statusColor = 0xFFFF5555;
        }
    }

    @Override
    protected void init() {
        servers = AccountStore.get().authServers;
        int x = this.width / 2 - BOX_WIDTH / 2;
        int y = 36;

        if (existing == null) {
            serverValues = new ArrayList<>(servers.stream().map(s -> s.name).toList());
            serverValues.add(CUSTOM_OPTION);
            if (!servers.isEmpty() && servers.stream().noneMatch(s -> s.name.equals(selectedServer))) {
                selectedServer = servers.get(0).name;
            }
            serverButton = Button.builder(Component.empty(), b -> serverListOpen = !serverListOpen)
                    .bounds(x, y, BOX_WIDTH, 20).build();
            serverButton.setMessage(serverDisplay(selectedServer));
            this.addRenderableWidget(serverButton);
            y += 24;

            usernameBox = new EditBox(this.font, x, y, BOX_WIDTH, 20, Component.translatable("accountswitcher.username"));
            usernameBox.setMaxLength(256);
            usernameBox.setHint(Component.translatable("accountswitcher.username"));
            this.addRenderableWidget(usernameBox);
            y += 24;
        }

        // Re-authentication only asks for the password; identity/server are known
        passwordBox = new EditBox(this.font, x, y, BOX_WIDTH, 20, Component.translatable("accountswitcher.password"));
        passwordBox.setMaxLength(256);
        passwordBox.setHint(Component.translatable("accountswitcher.password"));
        setPasswordMask();
        this.addRenderableWidget(passwordBox);
        y += 28;

        loginButton = Button.builder(Component.translatable("accountswitcher.login"), b -> startLogin())
                .bounds(x, y, 108, 20).build();
        this.addRenderableWidget(loginButton);
        this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.cancel"),
                        b -> onClose())
                .bounds(x + BOX_WIDTH - 108, y, 108, 20).build());
    }

    private void setPasswordMask() {
        // Preprocessed to addFormatter on 1.21.10+ via mapping-1.21.10-1.21.8.txt
        passwordBox.setFormatter((s, i) -> Component.literal("*".repeat(s.length())).getVisualOrderText());
    }

    private static Component serverDisplay(String value) {
        return CUSTOM_OPTION.equals(value)
                ? Component.translatable("accountswitcher.server.custom")
                : Component.literal(value);
    }

    /** Popup list geometry, shared by rendering and hit-testing. */
    private int listY() {
        return serverButton.getY() + serverButton.getHeight();
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void renderServerList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = serverButton.getX();
        int w = serverButton.getWidth();
        int y = listY();
        int h = serverValues.size() * ROW_HEIGHT;
        graphics.fill(x, y, x + w, y + h, 0xF0101010);
        // 1px border
        graphics.fill(x, y, x + w, y + 1, 0xFF505050);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF505050);
        graphics.fill(x, y, x + 1, y + h, 0xFF505050);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF505050);
        for (int i = 0; i < serverValues.size(); i++) {
            String value = serverValues.get(i);
            int ry = y + i * ROW_HEIGHT;
            if (inRect(mouseX, mouseY, x, ry, w, ROW_HEIGHT)) {
                graphics.fill(x + 1, ry + 1, x + w - 1, ry + ROW_HEIGHT - 1, 0xFF2D2D2D);
            }
            int color = value.equals(selectedServer) ? 0xFFFFAA00 : 0xFFE0E0E0;
            // Preprocessed to text() on 26.x via mapping-1.21.11-26.1.2.txt
            graphics.drawString(this.font, serverDisplay(value), x + 4, ry + 3, color);
        }
    }

    private void pickServer(String value) {
        serverListOpen = false;
        if (CUSTOM_OPTION.equals(value)) {
            // Coming back re-runs init(), so the dropdown picks up the new server
            this.minecraft.setScreen(new AuthServerEditScreen(this));
            return;
        }
        selectedServer = value;
        serverButton.setMessage(serverDisplay(value));
    }

    private boolean onScreenClicked(double mouseX, double mouseY) {
        if (serverListOpen && serverButton != null) {
            int x = serverButton.getX();
            int w = serverButton.getWidth();
            int y = listY();
            int h = serverValues.size() * ROW_HEIGHT;
            if (inRect(mouseX, mouseY, x, y, w, h)) {
                int idx = (int) ((mouseY - y) / ROW_HEIGHT);
                pickServer(serverValues.get(idx));
            } else {
                // Consume everything else while open: clicking the toggle button
                // just closes; clicking a widget under the popup must not activate it
                serverListOpen = false;
            }
            return true;
        }
        return false;
    }

    // 1.21.10+ changed the mouse event signature to (MouseButtonEvent, boolean)
    //#if MC >= 12110
    //$$ @Override
    //$$ public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
    //$$     return onScreenClicked(event.x(), event.y()) || super.mouseClicked(event, doubled);
    //$$ }
    //#else
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onScreenClicked(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }
    //#endif

    private void startLogin() {
        if (pending != null) {
            return;
        }
        com.accountswitcher.account.AuthServer server;
        String username;
        if (existing != null) {
            server = AccountStore.get().serverByUrl(existing.authServer)
                    .orElse(new com.accountswitcher.account.AuthServer(existing.authServer, existing.authServer));
            username = existing.username;
        } else {
            server = servers.stream().filter(s -> s.name.equals(selectedServer)).findFirst().orElse(null);
            username = usernameBox.getValue().trim();
        }
        String password = passwordBox.getValue();
        if (server == null || username.isEmpty() || password.isEmpty()) {
            statusColor = 0xFFFF5555;
            status = Component.translatable("accountswitcher.error.missingInput").getString();
            return;
        }
        pendingUsername = username;
        pendingPassword = password;
        pendingServer = server;
        statusColor = 0xFFAAAAAA;
        status = Component.translatable("accountswitcher.authenticating").getString();
        loginButton.active = false;
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                return YggdrasilAuth.authenticate(server.url, username, password);
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
        YggdrasilAuth.AuthResult result;
        try {
            result = pending.join();
        } catch (Exception e) {
            pending = null;
            loginButton.active = true;
            statusColor = 0xFFFF5555;
            status = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
            return;
        }
        pending = null;
        if (existing != null) {
            // Re-authentication keeps the stored profile choice; no profile screen
            YggdrasilAuth.Profile profile = YggdrasilAuth.pickProfile(result, existing.profileId);
            if (profile != null) {
                finishLogin(result, profile);
            } else {
                loginButton.active = true;
                statusColor = 0xFFFF5555;
                status = Component.translatable("accountswitcher.error.noProfile").getString();
            }
            return;
        }
        if (result.availableProfiles.size() > 1) {
            status = "";
            this.minecraft.setScreen(new ProfileSelectScreen(this, pendingServer, pendingUsername, pendingPassword, result));
        } else if (result.selectedProfile != null) {
            finishLogin(result, result.selectedProfile);
        } else if (!result.availableProfiles.isEmpty()) {
            finishLogin(result, result.availableProfiles.get(0));
        } else {
            loginButton.active = true;
            statusColor = 0xFFFF5555;
            status = Component.translatable("accountswitcher.error.noProfile").getString();
        }
    }

    private void finishLogin(YggdrasilAuth.AuthResult result, YggdrasilAuth.Profile profile) {
        Account account = existing != null ? existing : new Account();
        account.type = Account.Type.YGGDRASIL;
        account.authServer = pendingServer.url;
        account.username = pendingUsername;
        account.profileId = profile.id;
        account.profileName = profile.name;
        SessionController.applyYggdrasil(account, result.accessToken);
        SessionController.cachePassword(account.key(), pendingPassword);
        AccountStore.get().put(account);
        AccountStore.get().setActive(account.key());
        this.minecraft.setScreen(parent);
    }

    /** Leaves to the screen behind this one after a completed login (used by ProfileSelectScreen). */
    public void closeAfterLogin() {
        this.minecraft.setScreen(parent);
    }

    // Preprocessed to extractRenderState(GuiGraphicsExtractor, ...) on 26.x via mapping-1.21.11-26.1.2.txt
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        // Drawn after super so the popup covers the widgets below it
        if (serverListOpen && serverButton != null) {
            renderServerList(graphics, mouseX, mouseY);
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
