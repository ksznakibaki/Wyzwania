package pl.filip.wyzwania;

import io.papermc.paper.event.inventory.ItemCraftedEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import java.util.*;
import static pl.filip.wyzwania.Model.*;

public final class ActivityListener implements Listener {
    private final WyzwaniaPlugin plugin;
    private final Map<UUID, Sample> movement = new HashMap<>();
    private record Sample(Location location, UUID vehicle) {}
    public ActivityListener(WyzwaniaPlugin plugin) { this.plugin = plugin; }
    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
    private static boolean ore(Material type) { return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS; }
    private void add(Player player, Type type, String target, double amount) {
        plugin.advance(player, type, target.equals("ANY") ? Set.of("ANY") : Set.of("ANY", target), amount);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (ore(event.getBlockPlaced().getType())) {
            plugin.engine.state.placedOres.add(key(event.getBlockPlaced())); plugin.saveAsync();
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mine(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean placed = plugin.engine.state.placedOres.remove(key(block));
        if (placed) { plugin.saveAsync(); return; }
        add(event.getPlayer(), Type.MINE, block.getType().name(), 1);
    }
    private void piston(List<Block> blocks, BlockFace direction) {
        Set<String> moved = new HashSet<>();
        boolean changed = false;
        for (Block block : blocks) {
            if (plugin.engine.state.placedOres.remove(key(block))) {
                moved.add(key(block.getRelative(direction))); changed = true;
            }
        }
        if (changed) { plugin.engine.state.placedOres.addAll(moved); plugin.saveAsync(); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent event) { piston(event.getBlocks(), event.getDirection()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent event) {
        // Derive movement from the piston face, independently of event direction conventions.
        BlockFace facing = ((org.bukkit.block.data.Directional) event.getBlock().getBlockData()).getFacing();
        piston(event.getBlocks(), facing.getOppositeFace());
    }
    private void explode(List<Block> blocks) {
        boolean changed = false;
        for (Block block : blocks) changed |= plugin.engine.state.placedOres.remove(key(block));
        if (changed) plugin.saveAsync();
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) { explode(event.blockList()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(BlockExplodeEvent event) { explode(event.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void kill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player victim) {
            if (!plugin.settings.pvp || killer.equals(victim) || !plugin.eligible(victim) || !plugin.eligible(killer)) return;
            String pair = killer.getUniqueId() + ":" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            if (now - plugin.engine.state.pvpKills.getOrDefault(pair, 0L) < plugin.settings.pvpKillCooldown) return;
            plugin.engine.state.pvpKills.put(pair, now);
            add(killer, Type.PVP_KILL, "ANY", 1); plugin.saveAsync();
        } else {
            Set<String> targets = new HashSet<>(); targets.add(event.getEntityType().name());
            if (event.getEntity() instanceof Enemy) targets.add("ANY");
            plugin.advance(killer, Type.KILL, targets, 1);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (!plugin.settings.pvp || !(event.getEntity() instanceof Player victim) || !plugin.eligible(victim)) return;
        Entity damager = event.getDamager();
        Player attacker = damager instanceof Player p ? p
                : damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
        if (attacker == null || attacker.equals(victim) || !plugin.eligible(attacker)) return;
        double amount = Math.min(victim.getHealth(), event.getFinalDamage());
        if (amount <= 0) return;
        String pair = attacker.getUniqueId() + ":" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - plugin.engine.state.pvpDamage.getOrDefault(pair, 0L) < plugin.settings.pvpDamageCooldown) return;
        plugin.engine.state.pvpDamage.put(pair, now);
        add(attacker, Type.PVP_DAMAGE, "ANY", amount); plugin.saveAsync();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void craft(ItemCraftedEvent event) {
        add(event.getPlayer(), Type.CRAFT, event.getCraftedItem().getType().name(), event.getCraftedItem().getAmount());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void eat(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().isEdible()) add(event.getPlayer(), Type.EAT, event.getItem().getType().name(), 1);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) return;
        Material type = caught.getItemStack().getType();
        Set<String> targets = new HashSet<>(); targets.add("ANY");
        if (Set.of(Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH).contains(type)) targets.add("FISH");
        if (Set.of(Material.BOW, Material.ENCHANTED_BOOK, Material.FISHING_ROD, Material.NAME_TAG, Material.NAUTILUS_SHELL, Material.SADDLE).contains(type)) targets.add("TREASURE");
        plugin.advance(event.getPlayer(), Type.FISH, targets, 1);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) add(player, Type.BREED, event.getEntityType().name(), 1);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shear(PlayerShearEntityEvent event) { add(event.getPlayer(), Type.SHEAR, event.getEntity().getType().name(), 1); }

    /** One sample per tick also covers mounted animals, which need not emit player movement events. */
    public void sampleMovement() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Entity vehicle = player.getVehicle();
            boolean mount = vehicle instanceof Boat || vehicle instanceof Animals;
            if (!plugin.eligible(player) || player.isGliding() || player.isFlying() || player.isRiptiding()
                    || (vehicle != null && !mount) || (vehicle == null && (player.isSwimming() || player.isInWater()))) {
                movement.remove(player.getUniqueId()); continue;
            }
            Location location = player.getLocation();
            UUID vehicleId = vehicle == null ? null : vehicle.getUniqueId();
            Sample before = movement.put(player.getUniqueId(), new Sample(location, vehicleId));
            if (before == null || !Objects.equals(before.vehicle(), vehicleId) || !before.location().getWorld().equals(location.getWorld())) continue;
            double dx = location.getX() - before.location().getX(), dz = location.getZ() - before.location().getZ();
            double distance = Math.hypot(dx, dz);
            if (distance > 0 && distance <= plugin.settings.movementMaxStep) add(player, Type.MOVE, "ANY", distance);
        }
    }
    private void forget(Entity entity) {
        movement.remove(entity.getUniqueId()); entity.getPassengers().forEach(this::forget);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { forget(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(EntityTeleportEvent event) { forget(event.getEntity()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { forget(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { forget(event.getPlayer()); plugin.saveAsync(); }
}
