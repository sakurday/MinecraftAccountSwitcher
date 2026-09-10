package com.accountswitcher;

import com.accountswitcher.account.AccountStore;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point.
 *
 * Global account manager supporting three login types:
 *  - Premium: the launcher session (single, no in-mod login);
 *  - Third-party: authlib-injector (Yggdrasil) servers, multiple accounts, password re-entered per launch;
 *  - Offline: name-only accounts, multiple allowed.
 */
public class AccountSwitcher implements ClientModInitializer {
    public static final String MOD_ID = "accountswitcher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // Merge preset auth servers into the persisted config on first run
        AccountStore.get().save();
        LOGGER.info("Account Switcher loaded.");
    }
}
