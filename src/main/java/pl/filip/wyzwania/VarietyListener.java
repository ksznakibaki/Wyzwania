package pl.filip.wyzwania;

import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.*;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import static pl.filip.wyzwania.Model.*;

/** Variety objectives and attribution of manual brewing/repair actions. All handlers run on the server thread. */
public final class VarietyListener implements Listener {
    private final WyzwaniaPlugin plugin;
    private final NamespacedKey brewer, ingredientKey;
    private final Set<UUID> pendingRepairs = new HashSet<>();
    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH);
    public VarietyListener(WyzwaniaPlugin plugin) {
        this.plugin = plugin; brewer = new NamespacedKey(plugin, "brewer"); ingredientKey = new NamespacedKey(plugin, "ingredient");
    }
    private void add(Player player, Type type, String target, double amount) {
        plugin.advance(player, type, target.equals("ANY") ? Set.of("ANY") : Set.of("ANY", target), amount);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void eat(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().isEdible()) add(event.getPlayer(), Type.EAT_UNIQUE, event.getItem().getType().name(), 1);
    }
    public void sampleBiomes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.eligible(player)) continue;
            NamespacedKey key = player.getLocation().getBlock().getBiome().getKey();
            Set<String> targets = new HashSet<>(List.of("ANY", key.toString().toUpperCase(Locale.ROOT), key.getKey().toUpperCase(Locale.ROOT)));
            plugin.advance(player, Type.BIOME, targets, 1);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void harvest(BlockBreakEvent event) {
        if (CROPS.contains(event.getBlock().getType()) && event.getBlock().getBlockData() instanceof Ageable age
                && age.getAge() == age.getMaximumAge()) add(event.getPlayer(), Type.HARVEST, event.getBlock().getType().name(), 1);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void berries(PlayerHarvestBlockEvent event) {
        if (event.getHarvestedBlock().getType() == Material.SWEET_BERRY_BUSH
                && event.getHarvestedBlock().getBlockData() instanceof Ageable age && age.getAge() >= 2)
            add(event.getPlayer(), Type.HARVEST, "SWEET_BERRY_BUSH", 1);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trade(PlayerTradeEvent event) {
        add(event.getPlayer(), Type.TRADE, event.getVillager().getType().name(), 1);
    }
    private static ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }
    private void watchIngredient(BrewerInventory inventory, Player player) {
        BrewingStand holder = inventory.getHolder();
        if (holder == null) return;
        ItemStack before = copy(inventory.getIngredient());
        var block = holder.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(block.getState() instanceof BrewingStand stand)) return;
            ItemStack after = stand.getInventory().getIngredient();
            boolean inserted = after != null && !after.getType().isAir()
                    && (before == null || !after.isSimilar(before) || after.getAmount() > before.getAmount());
            if (!inserted) return;
            var pdc = stand.getPersistentDataContainer();
            if (player == null || !plugin.eligible(player)) { pdc.remove(brewer); pdc.remove(ingredientKey); }
            else {
                pdc.set(brewer, PersistentDataType.STRING, player.getUniqueId().toString());
                pdc.set(ingredientKey, PersistentDataType.STRING, after.getType().name());
            }
            stand.update();
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory() instanceof BrewerInventory inventory) watchIngredient(inventory, player);
        if (event.getView().getTopInventory() instanceof AnvilInventory anvil && event.getRawSlot() == 2)
            repair(event, player, anvil);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getRawSlots().contains(3)
                && event.getView().getTopInventory() instanceof BrewerInventory inventory) watchIngredient(inventory, player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent event) {
        if (event.getDestination() instanceof BrewerInventory inventory) watchIngredient(inventory, null);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void brew(BrewEvent event) {
        if (!(event.getBlock().getState() instanceof BrewingStand stand)) return;
        var pdc = stand.getPersistentDataContainer();
        String owner = pdc.get(brewer, PersistentDataType.STRING);
        ItemStack ingredient = event.getContents().getIngredient();
        if (owner == null || ingredient == null || !ingredient.getType().name().equals(pdc.get(ingredientKey, PersistentDataType.STRING))) return;
        Player player;
        try { player = Bukkit.getPlayer(UUID.fromString(owner)); } catch (IllegalArgumentException ignored) { return; }
        if (player == null || !plugin.eligible(player) || !player.getWorld().equals(event.getBlock().getWorld())) return;
        for (int slot = 0; slot < Math.min(3, event.getResults().size()); slot++) {
            ItemStack result = event.getResults().get(slot);
            if (result == null || !(result.getItemMeta() instanceof PotionMeta meta) || meta.getBasePotionType() == null) continue;
            String potion = meta.getBasePotionType().name();
            if (Set.of("WATER", "AWKWARD", "MUNDANE", "THICK").contains(potion)) continue;
            ItemStack before = event.getContents().getItem(slot);
            if (before == null || before.isSimilar(result)) continue;
            add(player, Type.BREW, potion, 1);
        }
    }
    private void repair(InventoryClickEvent event, Player player, AnvilInventory inventory) {
        if (!plugin.eligible(player) || pendingRepairs.contains(player.getUniqueId())) return;
        ItemStack before = copy(inventory.getItem(0)), result = event.getCurrentItem();
        if (before == null || result == null || !(before.getItemMeta() instanceof Damageable old)
                || !(result.getItemMeta() instanceof Damageable repaired) || old.getDamage() <= repaired.getDamage()) return;
        switch (event.getAction()) {
            case NOTHING, CLONE_STACK, UNKNOWN -> { return; }
            case MOVE_TO_OTHER_INVENTORY -> { if (!Rewards.fits(player.getInventory().getStorageContents(), List.of(result))) return; }
            default -> { }
        }
        if (inventory.getItem(1) == null) return;
        if (event.getView() instanceof org.bukkit.inventory.view.AnvilView view
                && (view.getRepairCost() <= 0 || player.getLevel() < view.getRepairCost()
                || view.getRepairCost() >= view.getMaximumRepairCost())) return;
        pendingRepairs.add(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingRepairs.remove(player.getUniqueId());
            ItemStack remaining = inventory.getItem(0);
            // A preview/rename without consuming the damaged input does not count.
            // Empty/AIR also differs from the original damaged item; no registry lookup needed.
            if (remaining == null || !remaining.equals(before)) add(player, Type.ANVIL_REPAIR, "ANY", 1);
        });
    }
}
