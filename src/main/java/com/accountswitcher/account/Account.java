package com.accountswitcher.account;

/** A stored account entry. Passwords are never persisted; switching back to a
 * Yggdrasil account within one client run uses the in-memory password cache. */
public class Account {
    public enum Type { PREMIUM, YGGDRASIL, OFFLINE }

    public Type type = Type.OFFLINE;
    /** YGGDRASIL only: Yggdrasil API root of the auth server. */
    public String authServer = "";
    /** YGGDRASIL: login username; OFFLINE: player name. */
    public String username = "";
    /** YGGDRASIL only: profile UUID without dashes. */
    public String profileId = "";
    /** YGGDRASIL only: selected profile name. */
    public String profileName = "";

    public String key() {
        return switch (type) {
            case PREMIUM -> "premium";
            case OFFLINE -> "offline:" + username.toLowerCase();
            case YGGDRASIL -> "ygg:" + authServer.toLowerCase() + "|" + profileId.toLowerCase();
        };
    }

    /** Whether this entry describes the same identity as another one. */
    public boolean sameIdentity(Account other) {
        return key().equals(other.key());
    }
}
