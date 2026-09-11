package com.accountswitcher.account;

import com.accountswitcher.AccountSwitcher;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persisted state: config/accountswitcher.json
 *
 * Stores saved accounts and known auth servers. Passwords and tokens are never
 * persisted; passwords live in a per-launch runtime cache only.
 */
public class AccountStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("accountswitcher.json");

    /** Preset auth servers, merged into the persisted list on load. */
    private static final List<AuthServer> PRESETS = List.of(
            new AuthServer("LittleSkin", "https://littleskin.cn/api/yggdrasil"),
            new AuthServer("MoeSkin", "https://moeskin.cn/api/yggdrasil"));

    public List<Account> accounts = new ArrayList<>();
    public List<AuthServer> authServers = new ArrayList<>();
    /** Key of the account last activated in the UI (informational). */
    public String activeKey = "";

    /** UI tweakables, editable in config/accountswitcher.json. */
    public static class UiConfig {
        public int openButtonX = 5;
        public int openButtonY = 6;
        public int openButtonWidth = 100;
        public int openButtonHeight = 20;
    }

    public UiConfig ui = new UiConfig();

    private static AccountStore instance;

    public static synchronized AccountStore get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AccountStore load() {
        AccountStore store = new AccountStore();
        if (Files.exists(CONFIG_PATH)) {
            try {
                AccountStore parsed = GSON.fromJson(Files.readString(CONFIG_PATH), AccountStore.class);
                if (parsed != null) {
                    store = parsed;
                    if (store.accounts == null) store.accounts = new ArrayList<>();
                    if (store.authServers == null) store.authServers = new ArrayList<>();
                    if (store.ui == null) store.ui = new UiConfig();
                }
            } catch (Exception e) {
                AccountSwitcher.LOGGER.error("[AccountSwitcher] Failed to read config", e);
            }
        }
        for (AuthServer preset : PRESETS) {
            if (!store.authServers.contains(preset)) {
                store.authServers.add(preset);
            }
        }
        return store;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            AccountSwitcher.LOGGER.error("[AccountSwitcher] Failed to save config", e);
        }
    }

    public Optional<Account> find(String key) {
        return accounts.stream().filter(a -> a.key().equals(key)).findFirst();
    }

    /** Adds or replaces an account identified by its key, then saves. */
    public void put(Account account) {
        accounts.removeIf(a -> a.key().equals(account.key()));
        accounts.add(account);
        save();
    }

    public void remove(String key) {
        accounts.removeIf(a -> a.key().equals(key));
        if (activeKey.equals(key)) {
            activeKey = "";
        }
        save();
    }

    public void setActive(String key) {
        this.activeKey = key;
        save();
    }

    public Optional<AuthServer> serverByUrl(String url) {
        return authServers.stream().filter(s -> s.url.equalsIgnoreCase(url)).findFirst();
    }

    /** Adds a custom auth server if absent; returns the stored entry. */
    public AuthServer addServer(AuthServer server) {
        Optional<AuthServer> existing = serverByUrl(server.url);
        if (existing.isPresent()) {
            existing.get().name = server.name;
            save();
            return existing.get();
        }
        authServers.add(server);
        save();
        return server;
    }
}
