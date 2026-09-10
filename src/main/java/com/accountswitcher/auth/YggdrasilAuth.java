package com.accountswitcher.auth;

import com.accountswitcher.account.AuthServer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generic Yggdrasil (authlib-injector) auth client, pure JDK HTTP.
 *
 * All endpoint paths follow the authlib-injector convention:
 *   {root}/authserver/authenticate, /authserver/refresh,
 *   {root}/sessionserver/session/minecraft/join
 */
public class YggdrasilAuth {
    /** AN Yggdrasil game profile. */
    public static class Profile {
        public String id;    // UUID without dashes
        public String name;
    }

    /** Result of authenticate. */
    public static class AuthResult {
        public String accessToken;
        public String clientToken;
        public List<Profile> availableProfiles = new ArrayList<>();
        public Profile selectedProfile;
    }

    /** authenticate: username/password login. */
    public static AuthResult authenticate(String root, String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        body.add("agent", agent);
        body.addProperty("username", username);
        body.addProperty("password", password);
        body.addProperty("requestUser", false);
        return parseAuthResponse(request("POST", root + "/authserver/authenticate", body.toString()));
    }

    /**
     * Picks the preferred profile: exact id match first (stored choice), then the
     * server-selected one, then the first available; null when the account has none.
     */
    public static Profile pickProfile(AuthResult result, String preferredProfileId) {
        if (preferredProfileId != null && !preferredProfileId.isEmpty()) {
            for (Profile p : result.availableProfiles) {
                if (p.id.equalsIgnoreCase(preferredProfileId)) {
                    return p;
                }
            }
        }
        if (result.selectedProfile != null) {
            return result.selectedProfile;
        }
        return result.availableProfiles.isEmpty() ? null : result.availableProfiles.get(0);
    }

    private static AuthResult parseAuthResponse(JsonObject resp) throws IOException {
        if (resp.has("errorMessage")) {
            throw new IOException(resp.get("errorMessage").getAsString());
        }
        AuthResult r = new AuthResult();
        r.accessToken = resp.get("accessToken").getAsString();
        if (resp.has("clientToken") && !resp.get("clientToken").isJsonNull()) {
            r.clientToken = resp.get("clientToken").getAsString();
        }
        if (resp.has("availableProfiles")) {
            for (JsonElement e : resp.getAsJsonArray("availableProfiles")) {
                JsonObject p = e.getAsJsonObject();
                Profile profile = new Profile();
                profile.id = p.get("id").getAsString();
                profile.name = p.get("name").getAsString();
                r.availableProfiles.add(profile);
            }
        }
        if (resp.has("selectedProfile") && !resp.get("selectedProfile").isJsonNull()) {
            JsonObject p = resp.getAsJsonObject("selectedProfile");
            Profile profile = new Profile();
            profile.id = p.get("id").getAsString();
            profile.name = p.get("name").getAsString();
            r.selectedProfile = profile;
        }
        return r;
    }

    /** join: session server handshake, the equivalent of what the vanilla client does with Mojang. */
    public static void joinServer(String root, String accessToken, UUID profileId, String serverId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("selectedProfile", profileId.toString().replace("-", ""));
        body.addProperty("serverId", serverId);
        request("POST", root + "/sessionserver/session/minecraft/join", body.toString());
    }

    /**
     * Probes a user-entered URL and turns it into an AuthServer entry.
     * Accepts a host, a site root or a full Yggdrasil API root; the server name comes
     * from the authlib-injector metadata (meta.serverName).
     */
    public static AuthServer probeServer(String input) throws IOException {
        String trimmed = input.trim().replace("\\", "/");
        if (trimmed.isBlank()) {
            throw new IOException("Empty URL");
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        if (!trimmed.endsWith("/api/yggdrasil")) {
            candidates.add(trimmed + "/api/yggdrasil");
        }
        IOException last = null;
        for (String candidate : candidates) {
            try {
                JsonObject meta = request("GET", candidate, null);
                if (meta.has("meta")) {
                    JsonObject m = meta.getAsJsonObject("meta");
                    String name = m.has("serverName") && !m.get("serverName").isJsonNull()
                            ? m.get("serverName").getAsString() : hostOf(candidate);
                    return new AuthServer(name, candidate);
                }
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("Not a Yggdrasil server");
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static JsonObject request(String method, String url, String json) throws IOException {
        // URI.create(...).toURL() avoids the URL(String) constructor deprecated for removal in Java 20+
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "AccountSwitcher/0.2.0");
            conn.setRequestProperty("Accept", "application/json");
            if (json != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                // Try to surface the server-provided error message
                try {
                    JsonObject err = JsonParser.parseString(responseBody).getAsJsonObject();
                    if (err.has("errorMessage")) {
                        throw new IOException(err.get("errorMessage").getAsString());
                    }
                } catch (IOException e) {
                    throw e;
                } catch (Exception ignored) {
                }
                throw new IOException("HTTP " + code + " (" + URLEncoder.encode(url, StandardCharsets.UTF_8) + ")");
            }
            if (responseBody.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(responseBody).getAsJsonObject();
        } finally {
            conn.disconnect();
        }
    }

    private YggdrasilAuth() {
    }
}
