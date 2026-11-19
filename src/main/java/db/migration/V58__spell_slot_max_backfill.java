package db.migration;

import com.moo.charactermanagerservice.services.SpellSlotBackfill;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Moves every character onto server-owned spell-slot maximums: from here on
 * the {@code max} in {@code pc.spell_slots} is the class table plus the
 * Dungeon Master's bonus lines ({@code slot1} .. {@code slot9} in
 * {@code pc.stat_adjustments}), never a number the sheet sent.
 *
 * <p>A Java migration because the class table lives in Java
 * ({@code ClassProgression}) and both columns are JSON in TEXT. It sits in the
 * {@code db.migration} package so Flyway finds it at the same
 * {@code classpath:db/migration} location as the SQL files. This class is only
 * the loop — the decision for one row is {@link SpellSlotBackfill#plan}, which
 * is unit tested without a database:
 * <ul>
 *   <li>slots stored above what the character works out to are kept as a
 *       "Carried over" bonus line (slots a DM had typed in by hand);</li>
 *   <li>everything else gets the table's number — which is how Paladins and
 *       Rangers receive their first slots;</li>
 *   <li>{@code used} is kept, clamped to the new maximum.</li>
 * </ul>
 * A row whose JSON cannot be read is skipped and left as it is; a row that is
 * already right is not written.
 *
 * <p>Plain JDBC on Flyway's own connection, inside Flyway's transaction — no
 * Spring beans (they do not exist yet when Flyway runs), and nothing here
 * commits or closes the connection. Table names are unqualified like in the
 * SQL migrations: Flyway points the connection at the {@code character} schema.
 */
public class V58__spell_slot_max_backfill extends BaseJavaMigration {

    private static final Log LOG = LogFactory.getLog(V58__spell_slot_max_backfill.class);

    private record Row(long id, String clazz, Integer level, String spellSlots, String statAdjustments) {}

    private record Update(long id, SpellSlotBackfill.Change change) {}

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        // Read everything first, then write: no result set is left open across the updates.
        List<Row> rows = new ArrayList<>();
        try (Statement select = connection.createStatement();
             ResultSet rs = select.executeQuery(
                     "SELECT id, clazz, level, spell_slots, stat_adjustments FROM pc ORDER BY id")) {
            while (rs.next()) {
                int level = rs.getInt("level");
                rows.add(new Row(rs.getLong("id"), rs.getString("clazz"), rs.wasNull() ? null : level,
                        rs.getString("spell_slots"), rs.getString("stat_adjustments")));
            }
        }

        List<Update> updates = new ArrayList<>();
        int skipped = 0;
        for (Row row : rows) {
            try {
                Optional<SpellSlotBackfill.Change> change = SpellSlotBackfill.plan(
                        row.clazz(), row.level(), row.spellSlots(), row.statAdjustments());
                change.ifPresent(c -> updates.add(new Update(row.id(), c)));
            } catch (RuntimeException e) {
                // One odd row must not stop every other character from being migrated.
                skipped++;
                LOG.warn("Spell slot backfill skipped pc " + row.id() + ": " + e);
            }
        }

        if (!updates.isEmpty()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE pc SET spell_slots = ?, stat_adjustments = ? WHERE id = ?")) {
                for (Update u : updates) {
                    update.setString(1, u.change().spellSlots());
                    update.setString(2, u.change().statAdjustments());
                    update.setLong(3, u.id());
                    update.addBatch();
                }
                update.executeBatch();
            }
        }
        LOG.info("Spell slot backfill: " + updates.size() + " of " + rows.size()
                + " characters updated" + (skipped > 0 ? ", " + skipped + " skipped" : ""));
    }
}
