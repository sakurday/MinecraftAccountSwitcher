package com.accountswitcher.mixin;

import com.accountswitcher.SessionController;
import com.accountswitcher.auth.YggdrasilAuth;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * Login handshake interception: when a third-party account is active, the join request
 * is forwarded to that account's Yggdrasil session server instead of Mojang's.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin {
    //#if MC >= 12101
    @Redirect(
            method = "authenticateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V"
            )
    )
    private void accountswitcher_redirectJoinServer(MinecraftSessionService service, UUID profileId, String accessToken, String serverId)
            throws AuthenticationException {
        if (SessionController.isYggdrasilActive()) {
            try {
                YggdrasilAuth.joinServer(
                        SessionController.activeAuthServer(),
                        SessionController.currentAccessToken(),
                        profileId,
                        serverId);
            } catch (Exception e) {
                throw new AuthenticationException("AccountSwitcher join failed: " + e.getMessage(), e);
            }
        } else {
            service.joinServer(profileId, accessToken, serverId);
        }
    }
    //#else
    //$$ // 1.20.1 (authlib 4): joinServer takes a GameProfile
    //$$ @Redirect(
    //$$         method = "authenticateServer",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Lcom/mojang/authlib/GameProfile;Ljava/lang/String;Ljava/lang/String;)V"
    //$$         )
    //$$ )
    //$$ private void accountswitcher_redirectJoinServer(MinecraftSessionService service, com.mojang.authlib.GameProfile profile, String accessToken, String serverId)
    //$$         throws AuthenticationException {
    //$$     if (SessionController.isYggdrasilActive()) {
    //$$         try {
    //$$             YggdrasilAuth.joinServer(
    //$$                     SessionController.activeAuthServer(),
    //$$                     SessionController.currentAccessToken(),
    //$$                     profile.getId(),
    //$$                     serverId);
    //$$         } catch (Exception e) {
    //$$             throw new AuthenticationException("AccountSwitcher join failed: " + e.getMessage(), e);
    //$$         }
    //$$     } else {
    //$$         service.joinServer(profile, accessToken, serverId);
    //$$     }
    //$$ }
    //#endif
}
