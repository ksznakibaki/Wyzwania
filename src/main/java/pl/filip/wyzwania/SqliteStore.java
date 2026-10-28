package pl.filip.wyzwania;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;
import static pl.filip.wyzwania.Model.*;

/** A single writer orders autosaves and durable completion/claim barriers. */
public final class SqliteStore implements AutoCloseable {
    private final Gson gson = new GsonBuilder().create();
    private final Connection db;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "Wyzwania-SQLite"));

    public SqliteStore(Path path) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement sql = db.createStatement()) {
            sql.execute("PRAGMA journal_mode=WAL");
            sql.execute("PRAGMA synchronous=FULL");
            sql.execute("PRAGMA busy_timeout=5000");
            sql.execute("CREATE TABLE IF NOT EXISTS state (id INTEGER PRIMARY KEY CHECK(id=1), json TEXT NOT NULL)");
        }
    }
    public State load() throws SQLException {
        try (Statement sql = db.createStatement(); ResultSet rows = sql.executeQuery("SELECT json FROM state WHERE id=1")) {
            if (!rows.next()) return new State();
            var json = JsonParser.parseString(rows.getString(1)).getAsJsonObject();
            for (String key : new String[]{"schema", "day", "week", "daily", "weekly", "players", "placedOres", "pvpKills", "pvpDamage"}) {
                if (!json.has(key) || json.get(key).isJsonNull()) throw new IllegalStateException("Brak pola bazy danych: " + key);
            }
            State result = gson.fromJson(json, State.class);
            if (result == null || result.schema != 1 || result.players == null || result.daily == null
                    || result.weekly == null || result.placedOres == null || result.pvpKills == null || result.pvpDamage == null)
                throw new IllegalStateException("Nieprawidłowy lub nieobsługiwany format bazy. Przywróć kopię zapasową.");
            validate(result);
            return result;
        }
    }
    private static void validate(State state) {
        java.util.Map<String, Quest> quests = new java.util.HashMap<>();
        for (Quest quest : java.util.stream.Stream.concat(state.daily.stream(), state.weekly.stream()).toList()) {
            if (quest == null || quest.id() == null || quest.tier() == null || quest.title() == null
                    || quest.objectives() == null || quest.objectives().isEmpty() || quest.reward() == null
                    || quests.put(quest.id(), quest) != null) throw new IllegalStateException("Nieprawidłowe zapisane zadanie.");
            for (Objective objective : quest.objectives()) {
                if (objective == null || objective.type() == null || objective.target() == null
                        || !Double.isFinite(objective.goal()) || objective.goal() <= 0)
                    throw new IllegalStateException("Nieprawidłowy zapisany cel.");
            }
        }
        for (var entry : state.players.entrySet()) {
            java.util.UUID.fromString(entry.getKey());
            PlayerData player = entry.getValue();
            if (player == null || player.name == null || player.month == null || player.points < 0 || player.monthPoints < 0
                    || player.progress == null || player.receipts == null || player.selectedPrefix == null)
                throw new IllegalStateException("Nieprawidłowy zapis gracza.");
            for (var progress : player.progress.entrySet()) {
                Quest quest = quests.get(progress.getKey());
                Progress value = progress.getValue();
                if (value == null || value.values == null || (quest != null && value.values.length != quest.objectives().size()))
                    throw new IllegalStateException("Nieprawidłowy zapis postępu.");
                for (double amount : value.values) if (!Double.isFinite(amount) || amount < 0)
                    throw new IllegalStateException("Nieprawidłowa wartość postępu.");
            }
            java.util.Set<String> receipts = new java.util.HashSet<>();
            for (Receipt receipt : player.receipts) {
                if (receipt == null || receipt.id == null || !receipts.add(receipt.id) || receipt.reward == null
                        || receipt.delivery == null || receipt.questTitle == null)
                    throw new IllegalStateException("Nieprawidłowy zapis nagrody.");
            }
        }
    }
    public CompletableFuture<Void> save(State state) {
        // Serialize on the server thread before handing off the immutable snapshot.
        String snapshot = gson.toJson(state);
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement sql = db.prepareStatement("INSERT INTO state(id,json) VALUES(1,?) ON CONFLICT(id) DO UPDATE SET json=excluded.json")) {
                sql.setString(1, snapshot); sql.executeUpdate();
            } catch (SQLException e) { throw new CompletionException(e); }
        }, writer);
    }
    public void saveNow(State state) { save(state).join(); }
    @Override public void close() throws Exception {
        writer.shutdown();
        if (!writer.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Nie zakończono zapisu SQLite.");
        db.close();
    }
}
