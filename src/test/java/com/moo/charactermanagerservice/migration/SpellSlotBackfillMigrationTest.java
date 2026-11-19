package com.moo.charactermanagerservice.migration;

import db.migration.V58__spell_slot_max_backfill;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the things Flyway needs to pick up the V58 Java migration, without a
 * database: Flyway scans {@code classpath:db/migration} (application.properties)
 * for classes as well as SQL files, and reads the version out of the class
 * name. The row logic itself is covered by {@code SpellSlotBackfillTest}; the
 * loop around it runs here against a mocked JDBC connection.
 */
class SpellSlotBackfillMigrationTest {

    @Test
    void livesWhereFlywayScans_nextToTheSqlMigrations() {
        assertThat(V58__spell_slot_max_backfill.class.getPackageName()).isEqualTo("db.migration");
        assertThat(getClass().getResource("/db/migration/V58__spell_slot_max_backfill.class")).isNotNull();
        assertThat(getClass().getResource("/db/migration/V57__pc_stat_adjustments.sql")).isNotNull();
    }

    @Test
    void isAVersionedJavaMigration_numbered58() {
        JavaMigration migration = new V58__spell_slot_max_backfill();

        assertThat(migration).isInstanceOf(BaseJavaMigration.class);
        assertThat(migration.getVersion().getVersion()).isEqualTo("58");
        assertThat(migration.getDescription()).isEqualTo("spell slot max backfill");
        assertThat(migration.canExecuteInTransaction()).isTrue();
    }

    @Test
    void noSqlMigrationClaimsTheSameVersion() {
        assertThat(getClass().getResource("/db/migration/V58__spell_slot_max_backfill.sql")).isNull();
    }

    // --- the loop: read every pc row, write only the ones that change ---

    /** A connection whose pc table holds the given rows: {id, clazz, level, spell_slots, stat_adjustments}. */
    private static Connection connectionWith(PreparedStatement update, Object[]... rows) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        int[] at = {-1};
        when(rs.next()).thenAnswer(inv -> ++at[0] < rows.length);
        when(rs.getLong("id")).thenAnswer(inv -> rows[at[0]][0]);
        when(rs.getString("clazz")).thenAnswer(inv -> rows[at[0]][1]);
        when(rs.getInt("level")).thenAnswer(inv -> rows[at[0]][2] == null ? 0 : rows[at[0]][2]);
        when(rs.wasNull()).thenAnswer(inv -> rows[at[0]][2] == null);
        when(rs.getString("spell_slots")).thenAnswer(inv -> rows[at[0]][3]);
        when(rs.getString("stat_adjustments")).thenAnswer(inv -> rows[at[0]][4]);

        Statement select = mock(Statement.class);
        when(select.executeQuery(contains("FROM pc"))).thenReturn(rs);
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(select);
        when(connection.prepareStatement(contains("UPDATE pc SET spell_slots = ?, stat_adjustments = ?")))
                .thenReturn(update);
        return connection;
    }

    private static void migrate(Connection connection) throws Exception {
        Context context = mock(Context.class);
        when(context.getConnection()).thenReturn(connection);
        new V58__spell_slot_max_backfill().migrate(context);
    }

    @Test
    void migrate_writesOnlyTheRowsThatChange_andLeavesTheConnectionToFlyway() throws Exception {
        PreparedStatement update = mock(PreparedStatement.class);
        Connection connection = connectionWith(update,
                new Object[] {1L, "Wizard", 3, "{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":0}}", null},
                new Object[] {2L, "Paladin", 5, null, null},                                // gains slots
                new Object[] {3L, "Fighter", 5, "{}", null},
                new Object[] {4L, "Wizard", 1, "not json", null},                           // skipped
                new Object[] {5L, "Fighter", null, "{\"1\":{\"max\":2,\"used\":1}}", null}); // carried over

        migrate(connection);

        verify(update, times(2)).addBatch();
        verify(update).setLong(3, 2L);
        verify(update).setString(1, "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}");
        verify(update).setString(2, null);
        verify(update).setLong(3, 5L);
        verify(update).setString(1, "{\"1\":{\"max\":2,\"used\":1}}");
        verify(update).setString(2, "{\"slot1\":[{\"value\":2,\"label\":\"Carried over\"}]}");
        verify(update).executeBatch();
        // Flyway owns the transaction and the connection.
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    @Test
    void migrate_writesNothing_whenEveryRowIsAlreadyRight() throws Exception {
        PreparedStatement update = mock(PreparedStatement.class);
        Connection connection = connectionWith(update,
                new Object[] {1L, "Fighter", 5, null, null},
                new Object[] {2L, "Warlock", 5, "{\"3\":{\"max\":2,\"used\":2}}", null});

        migrate(connection);

        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void migrate_copesWithAnEmptyTable() throws Exception {
        PreparedStatement update = mock(PreparedStatement.class);
        Connection connection = connectionWith(update);

        migrate(connection);

        verify(connection, never()).prepareStatement(anyString());
    }
}
