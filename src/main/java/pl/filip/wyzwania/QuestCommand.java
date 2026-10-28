package pl.filip.wyzwania;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static pl.filip.wyzwania.Model.*;
import static pl.filip.wyzwania.WyzwaniaPlugin.message;

public final class QuestCommand implements CommandExecutor, TabCompleter {
    private final WyzwaniaPlugin plugin;
    public QuestCommand(WyzwaniaPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.ensurePeriods();
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> info(sender);
            case "ranking" -> ranking(sender, args);
            case "admin" -> admin(sender, args);
            case "reload" -> plugin.reloadSettings(sender);
            case "", "postep", "odbierz", "prefix" -> {
                if (!(sender instanceof Player player)) { message(sender, "Ta komenda jest przeznaczona dla gracza."); return true; }
                switch (action) {
                    case "odbierz" -> plugin.claim(player);
                    case "prefix" -> prefix(player, args);
                    default -> show(player, action.equals("postep"));
                }
            }
            default -> message(sender, "Użycie: /wyzwanie [postep|odbierz|ranking [miesiac]|info|prefix]");
        }
        return true;
    }
    private void show(Player player, boolean detailed) {
        PlayerData data = plugin.data(player);
        message(player, "Wspólne wyzwania — Twój postęp:");
        for (Quest quest : plugin.engine.active()) {
            Progress p = plugin.engine.progress(data, quest);
            String status = p.completed ? "UKOŃCZONE" : "W TOKU";
            message(player, "[" + tier(quest.tier()) + "] " + quest.title() + " — " + status);
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective o = quest.objectives().get(i);
                message(player, "  " + o.label() + ": " + number(p.values[i]) + "/" + number(o.goal()));
            }
            message(player, "  Nagroda: " + quest.reward().label() + ", " + quest.reward().xp() + " XP, " + quest.reward().points() + " pkt.");
        }
        var format = DateTimeFormatter.ofPattern("dd.MM HH:mm z", Locale.forLanguageTag("pl-PL"));
        Instant now = Instant.now();
        message(player, "Reset dzienny: " + plugin.settings.periods.nextDay(now).format(format)
                + "; tygodniowy: " + plugin.settings.periods.nextWeek(now).format(format));
        message(player, "Punkty: " + data.points + ". Odbiór: /wyzwanie odbierz");
        if (detailed) {
            for (Receipt r : data.receipts) {
                if (r.delivery != Delivery.CLAIMED) message(player, "Nagroda: " + r.questTitle + " — " + r.reward.label()
                        + (r.delivery == Delivery.READY ? " (do odbioru)" : " (wymaga kontroli administratora)"));
            }
        }
    }
    private void ranking(CommandSender sender, String[] args) {
        boolean monthly = args.length > 1 && args[1].equalsIgnoreCase("miesiac");
        String month = plugin.settings.periods.month(Instant.now());
        var players = new ArrayList<>(plugin.engine.state.players.values());
        java.util.function.ToLongFunction<PlayerData> score = p -> monthly ? (p.month.equals(month) ? p.monthPoints : 0) : p.points;
        players.sort(Comparator.comparingLong(score).reversed().thenComparing(p -> p.name, String.CASE_INSENSITIVE_ORDER));
        message(sender, monthly ? "Ranking miesiąca " + month + ":" : "Ranking ogólny:");
        for (int i = 0; i < Math.min(10, players.size()); i++) {
            PlayerData p = players.get(i);
            message(sender, (i + 1) + ". " + p.name + " — " + score.applyAsLong(p) + " pkt");
        }
        if (players.isEmpty()) message(sender, "Brak graczy w rankingu.");
    }
    private void info(CommandSender sender) {
        message(sender, "Codziennie 3 zadania: łatwe, średnie i trudne. Jedno tygodniowe losowane w poniedziałek.");
        message(sender, "Zadania i nagrody są wspólne, postęp i odbiór indywidualne. Tygodniowe wymaga spełnienia WSZYSTKICH celów.");
        message(sender, "Punkty przyznawane przy ukończeniu. Przedmioty i XP: /wyzwanie odbierz. Punkty nie są walutą.");
        message(sender, "Jedna czynność może zaliczać cele dzienne i tygodniowe. Liczą się także moby ze spawnerów.");
        message(sender, "Dystans: poziomo pieszo, łódką i na zwierzętach; bez elytry, teleportów, pływania i lotu.");
        for (Settings.Prefix prefix : plugin.settings.prefixes) message(sender, prefix.id() + ": od " + prefix.points() + " pkt");
        message(sender, "Ranking miesięczny: /wyzwanie ranking miesiac. Wybór tytułu: /wyzwanie prefix <id|auto>.");
    }
    private void prefix(Player player, String[] args) {
        PlayerData data = plugin.data(player);
        if (args.length < 2) {
            message(player, "Dostępne: auto, " + String.join(", ", plugin.settings.prefixes.stream()
                    .filter(p -> p.points() <= data.points).map(Settings.Prefix::id).toList())); return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.equals("auto") && plugin.settings.prefixes.stream().noneMatch(p -> p.id().equals(id) && p.points() <= data.points)) {
            message(player, "Nie masz odblokowanego tego prefixu."); return;
        }
        data.selectedPrefix = id;
        if (plugin.persist()) { plugin.updatePrefix(player); message(player, "Wybrany prefix: " + id); }
    }
    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wyzwania.admin")) { message(sender, "Brak uprawnień wyzwania.admin."); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("uzupelnij")) {
            plugin.importDefaultQuests(sender); return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("losuj")) {
            if (args.length > 3) message(sender, "Użycie: /wyzwanie admin losuj [dzienne|tygodniowe|wszystkie]");
            else plugin.rerollQuests(sender, args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "wszystkie");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("punkty")) {
            if (args.length != 4) message(sender, "Użycie: /wyzwanie admin punkty <nick|UUID> <liczba>");
            else plugin.grantPoints(sender, args[2], args[3]);
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("zapisz")) {
            if (plugin.persist()) message(sender, "Zapisano dane SQLite."); return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("niepewne")) {
            int count = 0;
            for (var entry : plugin.engine.state.players.entrySet()) {
                for (Receipt r : entry.getValue().receipts) {
                    if (r.delivery == Delivery.DELIVERING) {
                        message(sender, entry.getValue().name + " UUID=" + entry.getKey() + " ID=" + r.id + " — " + r.reward.label()); count++;
                    }
                }
            }
            message(sender, "Odbiory wymagające kontroli: " + count); return;
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("rozwiaz")) {
            PlayerData data = plugin.engine.state.players.get(args[2]);
            Receipt receipt = data == null ? null : data.receipts.stream().filter(r -> r.id.equals(args[3]) && r.delivery == Delivery.DELIVERING).findFirst().orElse(null);
            if (receipt == null) { message(sender, "Nie znaleziono niepewnego odbioru dla podanych UUID i ID."); return; }
            if (args[4].equalsIgnoreCase("potwierdz")) receipt.delivery = Delivery.CLAIMED;
            else if (args[4].equalsIgnoreCase("ponow")) receipt.delivery = Delivery.READY;
            else { message(sender, "Ostatni argument: potwierdz albo ponow."); return; }
            if (plugin.persist()) {
                plugin.getLogger().info("Administrator " + sender.getName() + " rozwiązał odbiór " + args[2] + " " + receipt.id + " -> " + receipt.delivery);
                message(sender, "Zapisano decyzję. Ponowienie udostępnia odbiór przez /wyzwanie odbierz.");
            }
            return;
        }
        message(sender, "Admin: uzupelnij | losuj [dzienne|tygodniowe|wszystkie] | punkty <nick|UUID> <liczba> | zapisz | niepewne | rozwiaz <UUID> <ID> <potwierdz|ponow>");
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(List.of("postep", "odbierz", "ranking", "info", "prefix"));
            if (sender.hasPermission("wyzwania.admin")) suggestions.addAll(List.of("admin", "reload"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("ranking")) suggestions.add("miesiac");
            if (args[0].equalsIgnoreCase("prefix") && sender instanceof Player player) {
                suggestions.add("auto");
                plugin.settings.prefixes.stream().filter(p -> p.points() <= plugin.data(player).points).forEach(p -> suggestions.add(p.id()));
            }
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("wyzwania.admin")) suggestions.addAll(List.of("uzupelnij", "losuj", "punkty", "zapisz", "niepewne", "rozwiaz"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("losuj")
                && sender.hasPermission("wyzwania.admin")) {
            suggestions.addAll(List.of("dzienne", "tygodniowe", "wszystkie"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("punkty")
                && sender.hasPermission("wyzwania.admin")) {
            plugin.engine.state.players.values().stream().map(p -> p.name).distinct().sorted().forEach(suggestions::add);
        }
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(partial)).toList();
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.0f", Math.floor(value)); }
    private static String tier(Tier tier) {
        return switch (tier) { case EASY -> "Łatwe"; case MEDIUM -> "Średnie"; case HARD -> "Trudne"; case WEEKLY -> "Tygodniowe"; };
    }
}
