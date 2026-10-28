package pl.filip.wyzwania;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import static pl.filip.wyzwania.Model.*;

public final class WyzwaniaPlugin extends JavaPlugin implements Listener {
    volatile Settings settings;
    QuestEngine engine;
    private SqliteStore store;
    private final Map<UUID, Component> chatPrefixes = new ConcurrentHashMap<>();
    private volatile boolean failed;
    private boolean ready;
    private TabDisplay tabDisplay;
    private BukkitTask autosaveTask, tabTask;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            // JavaPlugin#getConfig may fall back to defaults after logging malformed YAML.
            // Load strictly so administrator mistakes cannot silently change active rules.
            settings = loadSettings();
            store = new SqliteStore(getDataFolder().toPath().resolve("wyzwania.db"));
            engine = new QuestEngine(store.load(), settings.periods, settings.templates, settings::reward, new Random());
            engine.validateDailyPool();
            engine.rotate(Instant.now());
            engine.refreshPoints(tier -> settings.pools.get(tier).points());
            store.saveNow(engine.state);
            ready = true;
            var command = Objects.requireNonNull(getCommand("wyzwanie"));
            QuestCommand executor = new QuestCommand(this);
            command.setExecutor(executor); command.setTabCompleter(executor);
            ActivityListener activities = new ActivityListener(this);
            VarietyListener variety = new VarietyListener(this);
            getServer().getPluginManager().registerEvents(activities, this);
            getServer().getPluginManager().registerEvents(variety, this);
            getServer().getPluginManager().registerEvents(this, this);
            Bukkit.getScheduler().runTaskTimer(this, activities::sampleMovement, 1, 1);
            Bukkit.getScheduler().runTaskTimer(this, variety::sampleBiomes, 20, 20);
            Bukkit.getScheduler().runTaskTimer(this, this::ensurePeriods, 20, 20);
            tabDisplay = new TabDisplay(this);
            scheduleConfigTasks();
            Bukkit.getOnlinePlayers().forEach(this::register);
            long uncertain = engine.state.players.values().stream().flatMap(p -> p.receipts.stream())
                    .filter(r -> r.delivery == Delivery.DELIVERING).count();
            if (uncertain > 0) getLogger().warning("Nagrody wymagające kontroli po przerwanym odbiorze: " + uncertain + ". /wyzwanie admin niepewne");
            getLogger().info("Wyzwania gotowe. Dzienne: " + engine.state.day + ", tygodniowe: " + engine.state.week);
        } catch (Exception e) {
            failed = true;
            getLogger().log(Level.SEVERE, "Nie udało się uruchomić pluginu. Nie nadpisuję uszkodzonych danych.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if (tabDisplay != null) tabDisplay.clear();
        if (store == null) return;
        try {
            if (ready && !failed) store.saveNow(engine.state);
            store.close();
        } catch (Exception e) { getLogger().log(Level.SEVERE, "Błąd zamykania bazy wyzwań.", e); }
    }
    boolean eligible(Player player) {
        return ready && !failed && player.hasPermission("wyzwania.use")
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && (settings.worlds.isEmpty() || settings.worlds.contains(player.getWorld().getName()));
    }
    void ensurePeriods() {
        if (failed || !ready) return;
        try {
            if (engine.rotate(Instant.now())) {
                persist();
                Bukkit.getOnlinePlayers().forEach(p -> message(p, "Odświeżono wyzwania. Sprawdź /wyzwanie."));
            }
        } catch (Exception e) { storageFailure(e); }
    }
    void advance(Player player, Type type, Set<String> targets, double amount) {
        if (!eligible(player)) return;
        ensurePeriods();
        if (failed) return;
        var completed = engine.advance(player.getUniqueId(), player.getName(), type, targets, amount, Instant.now());
        if (!completed.isEmpty()) {
            if (!persist()) return;
            updatePrefix(player);
            for (Quest quest : completed) {
                message(player, "Ukończono: " + quest.title() + "! +" + quest.reward().points()
                        + " pkt. Nagroda czeka pod /wyzwanie odbierz.");
                if (settings.completionAnnouncements) {
                    String text = player.getName() + " ukończył wyzwanie "
                            + (quest.tier() == Tier.WEEKLY ? "tygodniowe" : "dzienne") + ": " + quest.title() + "!";
                    Bukkit.getOnlinePlayers().forEach(viewer -> message(viewer, text));
                    getLogger().info(text);
                }
            }
            if (tabDisplay != null) tabDisplay.update(player);
        }
    }
    boolean persist() {
        if (failed) return false;
        try { store.saveNow(engine.state); return true; }
        catch (Exception e) { storageFailure(e); return false; }
    }
    void saveAsync() {
        if (!ready || failed) return;
        store.save(engine.state).exceptionally(error -> {
            // Only the error flag and logger cross threads; all Bukkit work is rescheduled.
            failed = true;
            getLogger().log(Level.SEVERE, "Nie udało się zapisać SQLite; wstrzymano wyzwania.", error);
            Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
            return null;
        });
    }
    private void storageFailure(Exception error) {
        failed = true;
        getLogger().log(Level.SEVERE, "Błąd danych wyzwań; plugin zostaje wyłączony.", error);
        getServer().getPluginManager().disablePlugin(this);
    }
    PlayerData data(Player player) { return engine.player(player.getUniqueId(), player.getName()); }
    private void register(Player player) {
        data(player); updatePrefix(player);
        if (tabDisplay != null) tabDisplay.update(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        chatPrefixes.remove(event.getPlayer().getUniqueId());
        if (tabDisplay != null) tabDisplay.forget(event.getPlayer().getUniqueId());
    }
    private Settings loadSettings() throws Exception {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.load(getDataFolder().toPath().resolve("config.yml").toFile());
        return new Settings(config);
    }
    private void scheduleConfigTasks() {
        if (autosaveTask != null) autosaveTask.cancel();
        if (tabTask != null) tabTask.cancel();
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, this::saveAsync,
                settings.autosaveSeconds * 20L, settings.autosaveSeconds * 20L);
        if (settings.tab) tabTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!failed) tabDisplay.refresh();
        }, 1, settings.tabRefreshSeconds * 20L);
    }
    void reloadSettings(org.bukkit.command.CommandSender sender) {
        if (!sender.hasPermission("wyzwania.admin")) { message(sender, "Brak uprawnień wyzwania.admin."); return; }
        Settings replacement;
        try {
            replacement = loadSettings();
            new QuestEngine(engine.state, replacement.periods, replacement.templates, replacement::reward, new Random()).validateDailyPool();
        }
        catch (Exception error) {
            message(sender, "Nie przeładowano konfiguracji: " + error.getMessage() + ". Poprzednie ustawienia pozostają aktywne.");
            getLogger().log(Level.WARNING, "Odrzucono nieprawidłowy config.yml.", error);
            return;
        }
        // Update points in persisted quest copies without rerolling items, XP or objectives.
        boolean changedSchedule = !settings.periods.equals(replacement.periods);
        QuestEngine updated = new QuestEngine(engine.state, replacement.periods, replacement.templates, replacement::reward, new Random());
        updated.refreshPoints(tier -> replacement.pools.get(tier).points());
        if (!persist()) return;
        engine = updated;
        settings = replacement;
        scheduleConfigTasks();
        Bukkit.getOnlinePlayers().forEach(this::updatePrefix);
        tabDisplay.refresh();
        message(sender, "Przeładowano config.yml. Punktacja aktywnych zadań została zaktualizowana. Postęp, wylosowane przedmioty i XP zachowano. Już przyznane punkty pozostają bez zmian.");
        if (changedSchedule) message(sender, "Zmieniono harmonogram resetów. Inny dzień lub tydzień według nowych ustawień spowoduje normalne odświeżenie zadań.");
    }
    void rerollQuests(org.bukkit.command.CommandSender sender, String scope) {
        if (!sender.hasPermission("wyzwania.admin")) { message(sender, "Brak uprawnień wyzwania.admin."); return; }
        boolean daily = scope.equals("dzienne") || scope.equals("wszystkie");
        boolean weekly = scope.equals("tygodniowe") || scope.equals("wszystkie");
        if (!daily && !weekly) { message(sender, "Użycie: /wyzwanie admin losuj [dzienne|tygodniowe|wszystkie]"); return; }
        try { engine.reroll(Instant.now(), daily, weekly); }
        catch (IllegalArgumentException error) { message(sender, "Nie zmieniono wyzwań: " + error.getMessage()); return; }
        if (!persist()) return;
        if (tabDisplay != null) tabDisplay.refresh();
        String text = "Administrator wylosował nowe wyzwania: " + scope + ". Postęp tej grupy został wyzerowany; zaległe nagrody zachowano. /wyzwanie";
        Bukkit.getOnlinePlayers().forEach(player -> message(player, text));
        getLogger().info("Administrator " + sender.getName() + " wylosował wyzwania: " + scope);
        message(sender, "Wylosowano i zapisano nowe wyzwania: " + scope + ".");
    }
    void importDefaultQuests(org.bukkit.command.CommandSender sender) {
        if (!sender.hasPermission("wyzwania.admin")) { message(sender, "Brak uprawnień wyzwania.admin."); return; }
        try {
            var file = getDataFolder().toPath().resolve("config.yml").toFile();
            var config = new org.bukkit.configuration.file.YamlConfiguration(); config.load(file);
            var quests = config.getConfigurationSection("quests");
            if (quests == null) throw new IllegalArgumentException("Brak sekcji quests w config.yml.");
            var defaults = new org.bukkit.configuration.file.YamlConfiguration();
            try (var resource = Objects.requireNonNull(getResource("config.yml"))) {
                defaults.loadFromString(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            int added = QuestImport.addMissing(config, defaults);
            Settings candidate = new Settings(config);
            new QuestEngine(engine.state, candidate.periods, candidate.templates, candidate::reward, new Random()).validateDailyPool();
            config.save(file);
            message(sender, "Dodano brakujące szablony: " + added + ". Istniejące zadania i punktację zachowano.");
            reloadSettings(sender);
        } catch (Exception error) {
            message(sender, "Nie udało się uzupełnić zadań: " + error.getMessage());
            getLogger().log(Level.WARNING, "Błąd importu domyślnych zadań.", error);
        }
    }
    void grantPoints(org.bukkit.command.CommandSender sender, String target, String amountText) {
        if (!sender.hasPermission("wyzwania.admin")) { message(sender, "Brak uprawnień wyzwania.admin."); return; }
        UUID id;
        PlayerData data;
        long amount;
        try {
            amount = Long.parseLong(amountText);
            id = engine.resolvePlayer(target);
            data = engine.addPoints(id, amount, Instant.now());
        } catch (NumberFormatException error) {
            message(sender, "Podaj dodatnią liczbę całkowitą punktów."); return;
        } catch (ArithmeticException error) {
            message(sender, "Nie dodano punktów: przekroczono maksymalny wynik rankingu."); return;
        } catch (IllegalArgumentException error) {
            message(sender, error.getMessage()); return;
        }
        if (!persist()) return;
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            updatePrefix(online);
            if (tabDisplay != null) tabDisplay.update(online);
            message(online, "Administrator przyznał Ci " + amount + " punktów rankingu. Łącznie: " + data.points + ".");
        }
        getLogger().info("Administrator " + sender.getName() + " dodał " + amount + " pkt graczowi " + data.name
                + " (" + id + "). Wynik ogólny: " + data.points + ", miesięczny: " + data.monthPoints);
        message(sender, "Dodano " + amount + " pkt graczowi " + data.name + ". Wynik ogólny: " + data.points
                + ", miesięczny: " + data.monthPoints + ". Zapisano w bazie.");
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        register(event.getPlayer());
        long pending = data(event.getPlayer()).receipts.stream().filter(r -> r.delivery == Delivery.READY).count();
        if (pending > 0) message(event.getPlayer(), "Czekające nagrody: " + pending + ". /wyzwanie odbierz");
    }
    void updatePrefix(Player player) {
        chatPrefixes.put(player.getUniqueId(), MiniMessage.miniMessage().deserialize(settings.prefix(data(player)).text()));
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        if (!settings.chat || failed) return;
        Component prefix = chatPrefixes.get(event.getPlayer().getUniqueId());
        if (prefix == null) return;
        var previous = event.renderer();
        event.renderer((source, displayName, message, viewer) ->
                previous.render(source, prefix.append(Component.space()).append(displayName), message, viewer));
    }
    void claim(Player player) {
        if (!eligible(player)) { message(player, "Nagrody odbierzesz w dozwolonym świecie i trybie survival/adventure."); return; }
        PlayerData data = data(player);
        int issued = 0;
        for (Receipt receipt : data.receipts) {
            if (receipt.delivery != Delivery.READY) continue;
            var items = Rewards.items(receipt.reward);
            if (!Rewards.fits(player.getInventory().getStorageContents(), items)) {
                message(player, "Zwolnij miejsce na pełny pakiet: " + receipt.reward.label() + ". Nagroda pozostaje zapisana."); break;
            }
            receipt.delivery = Delivery.DELIVERING;
            if (!persist()) return;
            try {
                var leftovers = player.getInventory().addItem(items.toArray(org.bukkit.inventory.ItemStack[]::new));
                if (!leftovers.isEmpty()) throw new IllegalStateException("Niespodziewany brak miejsca podczas odbioru.");
                player.giveExp(receipt.reward.xp());
                player.saveData();
                receipt.delivery = Delivery.CLAIMED;
                if (!persist()) return;
                issued++;
                message(player, "Odebrano: " + receipt.reward.label() + " oraz " + receipt.reward.xp() + " XP.");
            } catch (Exception e) {
                // Never retry an ambiguous Minecraft inventory/SQLite transaction automatically.
                receipt.delivery = Delivery.DELIVERING;
                getLogger().log(Level.SEVERE, "Sprawdź odbiór gracza " + player.getUniqueId() + ", nagroda " + receipt.id, e);
                message(player, "Odbiór wymaga kontroli administratora. Nagroda nie zostanie wydana ponownie automatycznie.");
                return;
            }
        }
        if (issued == 0) message(player, "Brak wydanych nagród. Listę oczekujących znajdziesz pod /wyzwanie postep.");
    }
    static void message(org.bukkit.command.CommandSender sender, String text) {
        sender.sendMessage(Component.text("[Wyzwania] ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.WHITE)));
    }
}
