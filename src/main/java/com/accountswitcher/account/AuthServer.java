package com.accountswitcher.account;

/** A Yggdrasil (authlib-injector) auth server entry. */
public class AuthServer {
    public String name = "";
    public String url = "";

    public AuthServer() {
    }

    public AuthServer(String name, String url) {
        this.name = name;
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AuthServer a && a.url.equalsIgnoreCase(this.url);
    }

    @Override
    public int hashCode() {
        return url.toLowerCase().hashCode();
    }
}
