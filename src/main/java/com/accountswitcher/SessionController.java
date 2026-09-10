package com.accountswitcher;

import com.accountswitcher.account.Account;
import com.accountswitcher.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the active account to the game session by replacing {@code Minecraft#user}.
 *
 * Passwords are never persisted. A per-launch runtime cache keeps the Yggdrasil
 * password in memory only (static state dies with the JVM, i.e. when the client
 * closes), so switching between accounts within one session needs no re-entry;
 * the first switch after every launch asks for the password once.
 */
public final class SessionController {
    /** The launcher session, captured on first use, used for restore. */
    private static User originalUser;
    private static boolean initialized;

    /** Currently active Yggdrasil root, null when premium/offline is active. */
    private static volatile String activeAuthServer;
    private static volatile Account activeAccount;

    /** Per-launch token cache, keyed by account key. Never persisted. */
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    /** Per-launch Yggdrasil password cache, keyed by account key; cleared on client close. */
    private static final Map<String, String> PASSWORDS = new ConcurrentHashMap<>();

    private SessionController() {
    }

    public static synchronized void ensureInit() {
        if (initialized) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getUser() == null) {
            return;
        }
        originalUser = mc.getUser();
        initialized = true;
    }

    /** Premium (launcher) session available: current session UUID is not an offline v3 UUID. */
    public static boolean isPremiumSession() {
        ensureInit();
        return initialized && originalUser != null
                && originalUser.getProfileId() != null
                && originalUser.getProfileId().version() != 3;
    }

    public static User originalUser() {
        ensureInit();
        return originalUser;
    }

    public static void applyPremium() {
        ensureInit();
        if (originalUser != null) {
            setUser(originalUser);
        }
        activeAuthServer = null;
        activeAccount = null;
    }

    public static void applyOffline(String name) {
        ensureInit();
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        setUser(Sessions.create(name, uuid, "0", true));
        activeAuthServer = null;
        Account acc = new Account();
        acc.type = Account.Type.OFFLINE;
        acc.username = name;
        activeAccount = acc;
    }

    public static void applyYggdrasil(Account account, String accessToken) {
        ensureInit();
        UUID uuid = parseUuid(account.profileId);
        if (uuid == null) {
            throw new IllegalArgumentException("Invalid profile UUID: " + account.profileId);
        }
        setUser(Sessions.create(account.profileName, uuid, accessToken, false));
        activeAuthServer = account.authServer;
        activeAccount = account;
        TOKENS.put(account.key(), accessToken);
    }

    // ---- per-launch password cache ----

    public static void cachePassword(String accountKey, String password) {
        if (accountKey != null && password != null && !password.isEmpty()) {
            PASSWORDS.put(accountKey, password);
            AccountSwitcher.LOGGER.info("[AccountSwitcher] password cached for {}", accountKey);
        }
    }

    /** Runtime-cached password for the account, or null for this launch's first switch. */
    public static String cachedPassword(String accountKey) {
        String password = PASSWORDS.get(accountKey);
        AccountSwitcher.LOGGER.info("[AccountSwitcher] password cache {} for {}",
                password != null ? "hit" : "miss", accountKey);
        return password;
    }

    /**
     * Reactivates an account using its per-launch token from an earlier
     * authentication (no network). False when no such token exists yet.
     */
    public static boolean applyCachedYggdrasil(Account account) {
        String token = TOKENS.get(account.key());
        if (token == null) {
            return false;
        }
        AccountSwitcher.LOGGER.info("[AccountSwitcher] reusing per-launch token for {}", account.key());
        applyYggdrasil(account, token);
        return true;
    }

    // ---- handshake redirect support ----

    public static boolean isYggdrasilActive() {
        return activeAuthServer != null;
    }

    public static String activeAuthServer() {
        return activeAuthServer;
    }

    public static String currentAccessToken() {
        Account acc = activeAccount;
        return acc == null ? null : TOKENS.get(acc.key());
    }

    public static UUID currentProfileId() {
        Account acc = activeAccount;
        return acc == null ? null : parseUuid(acc.profileId);
    }

    static void setUser(User user) {
        ((MinecraftAccessor) Minecraft.getInstance()).accountswitcher_setUser(user);
    }

    static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String dashed = s;
        if (s.length() == 32) {
            dashed = s.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5");
        }
        try {
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
