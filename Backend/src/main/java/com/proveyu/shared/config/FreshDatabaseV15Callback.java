package com.proveyu.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.List;

/**
 * DEV / TEST ONLY. V15 seeds candidate_profiles for six hard-coded user ids that only exist in the
 * original shared database, so on an empty database V15 fails with a foreign-key error.
 * Rather than editing an already-applied migration, this callback inserts six disabled placeholder
 * users right before V15 runs, and only when the database is being built from scratch.
 * It never runs in production (no "dev"/"test" profile) and never runs on a DB where V15 is already applied.
 */
@Slf4j
@Component
@Profile({"dev", "test"})
public class FreshDatabaseV15Callback implements Callback {

    static final List<String> V15_USER_IDS = List.of(
            "0ce31fa4-8c75-4e6f-9369-19fc0525316c", "8d3d574a-d9ee-4ba0-a476-89583ffeb05e", "355a989a-77ff-4399-9644-e7bf42aba511",
            "2d5cfbae-879a-4b27-b498-614a8a02c810", "5647032a-a08f-4328-bb9f-bb63295af6bd", "7a914264-53dc-4986-94ac-c9566c713952");

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null
                && context.getMigrationInfo().getVersion() != null && "15".equals(context.getMigrationInfo().getVersion().getVersion());
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try (Statement st = context.getConnection().createStatement()) {
            for (String id : V15_USER_IDS) {
                st.executeUpdate("INSERT IGNORE INTO users (id, email, password_hash, role, full_name, is_active) VALUES ('" + id
                        + "', 'placeholder-" + id.substring(0, 8) + "@dev.invalid', '!', 'CANDIDATE', 'Dev placeholder (V15)', 0)");
            }
            log.warn("[DEV] Inserted {} disabled placeholder users so V15 can run on a fresh database", V15_USER_IDS.size());
        } catch (Exception e) {
            throw new IllegalStateException("Could not prepare fresh database for V15", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "FreshDatabaseV15Callback";
    }
}
