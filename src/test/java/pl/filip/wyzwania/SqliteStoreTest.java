package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.filip.wyzwania.Model.*;

class SqliteStoreTest {
    @TempDir Path temporary;
    @Test void restartRestoresQuestsProgressRewardsOresAndCooldowns() throws Exception {
        Path file = temporary.resolve("wyzwania.db");
        QuestEngine engine = QuestEngineTest.engine(new State());
        engine.rotate(QuestEngineTest.MONDAY);
        QuestEngineTest.add(engine, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 2, QuestEngineTest.MONDAY);
        engine.state.placedOres.add("world:1:2:3");
        engine.state.pvpKills.put("pair", 123L);
        engine.state.players.get(QuestEngineTest.ALICE.toString()).receipts.getFirst().delivery = Delivery.DELIVERING;
        try (SqliteStore store = new SqliteStore(file)) { store.saveNow(engine.state); }
        try (SqliteStore store = new SqliteStore(file)) {
            QuestEngine restored = QuestEngineTest.engine(store.load());
            assertFalse(restored.rotate(QuestEngineTest.MONDAY));
            assertEquals(engine.active(), restored.active());
            assertTrue(restored.state.placedOres.contains("world:1:2:3"));
            assertEquals(123L, restored.state.pvpKills.get("pair"));
            QuestEngineTest.add(restored, QuestEngineTest.ALICE, Type.MINE, "IRON_ORE", 1, QuestEngineTest.MONDAY);
            var p = restored.player(QuestEngineTest.ALICE, "Alice");
            assertEquals(10, p.points); assertEquals(1, p.receipts.size());
            assertEquals(Delivery.DELIVERING, p.receipts.getFirst().delivery);
        }
    }
    @Test void durableSaveCannotBeOverwrittenByEarlierAutosave() throws Exception {
        try (SqliteStore store = new SqliteStore(temporary.resolve("ordered.db"))) {
            State state = new State(); state.day = "old"; store.save(state);
            state.day = "new"; store.saveNow(state);
            assertEquals("new", store.load().day);
        }
    }
    @Test void corruptDatabaseDoesNotBecomeEmptyState() throws Exception {
        Path file = temporary.resolve("corrupt.db");
        try (SqliteStore ignored = new SqliteStore(file)) {}
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement sql = db.createStatement()) { sql.executeUpdate("INSERT INTO state VALUES(1, 'broken json')"); }
        try (SqliteStore store = new SqliteStore(file)) { assertThrows(RuntimeException.class, store::load); }
    }
    @Test void unsupportedSchemaFailsWithoutOverwritingData() throws Exception {
        Path file = temporary.resolve("future.db");
        try (SqliteStore store = new SqliteStore(file)) {
            State state = new State(); state.schema = 999; store.saveNow(state);
            assertThrows(IllegalStateException.class, store::load);
        }
    }
    @Test void missingFieldsCannotSilentlyResetServerState() throws Exception {
        Path file = temporary.resolve("missing.db");
        try (SqliteStore ignored = new SqliteStore(file)) {}
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement sql = db.createStatement()) { sql.executeUpdate("INSERT INTO state VALUES(1, '{}')"); }
        try (SqliteStore store = new SqliteStore(file)) { assertThrows(IllegalStateException.class, store::load); }
    }
}
