package pl.filip.wyzwania;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

/** Owns only the footer, leaving the player list and header untouched. */
final class TabDisplay {
    private record Footer(Component original, Component sent) {}
    private final Map<UUID, Footer> footers = new HashMap<>();
    private final WyzwaniaPlugin plugin;
    TabDisplay(WyzwaniaPlugin plugin) { this.plugin = plugin; }

    void refresh() { Bukkit.getOnlinePlayers().forEach(this::update); }
    void update(Player player) {
        if (!plugin.settings.tab || !player.hasPermission("wyzwania.use")) { restore(player); return; }
        Component current = player.playerListFooter();
        Footer previous = footers.get(player.getUniqueId());
        Component original = previous == null || !Objects.equals(current, previous.sent()) ? current : previous.original();
        Component summary = Component.text(TabSummary.text(plugin.engine.active(), plugin.data(player)), NamedTextColor.AQUA);
        Component next = original == null || original.equals(Component.empty()) ? summary
                : original.append(Component.newline()).append(Component.newline()).append(summary);
        if (!next.equals(current)) player.sendPlayerListFooter(next);
        footers.put(player.getUniqueId(), new Footer(original, next));
    }
    private void restore(Player player) {
        Footer footer = footers.remove(player.getUniqueId());
        // Do not erase a newer footer installed by another plugin.
        if (footer != null && Objects.equals(player.playerListFooter(), footer.sent()))
            player.sendPlayerListFooter(footer.original() == null ? Component.empty() : footer.original());
    }
    void clear() { Bukkit.getOnlinePlayers().forEach(this::restore); footers.clear(); }
    void forget(UUID player) { footers.remove(player); }
}
