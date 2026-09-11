package com.accountswitcher;

import net.minecraft.client.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Creates {@link User} instances. The constructor differs across versions:
 * 1.20.1 takes a String UUID, 1.21.1~1.21.8 take a trailing {@code User.Type},
 * 1.21.9+ drops the type entirely.
 */
final class Sessions {
    static User create(String name, UUID uuid, String accessToken, boolean offline) {
        //#if MC >= 12109
        //$$ // User.Type removed in 1.21.9
        //$$ return new User(name, uuid, accessToken, Optional.empty(), Optional.empty());
        //#elseif MC < 12101
        //$$ // 1.20.1 takes the profile UUID as a String
        //$$ return new User(name, uuid.toString(), accessToken, Optional.empty(), Optional.empty(),
        //$$         offline ? User.Type.LEGACY : User.Type.MSA);
        //#else
        return new User(name, uuid, accessToken, Optional.empty(), Optional.empty(),
                offline ? User.Type.LEGACY : User.Type.MSA); // no OFFLINE constant pre-26.x
        //#endif
    }

    private Sessions() {
    }
}
