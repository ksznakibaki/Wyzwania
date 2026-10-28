package pl.filip.wyzwania;

import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.*;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static pl.filip.wyzwania.Model.*;

class VarietyListenerTest {
    WyzwaniaPlugin plugin;
    VarietyListener listener;
    Player player;
    @BeforeEach void setup() {
        plugin = mock(WyzwaniaPlugin.class); when(plugin.getName()).thenReturn("Wyzwania"); when(plugin.namespace()).thenReturn("wyzwania");
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(QuestEngineTest.ALICE);
        when(plugin.eligible(player)).thenReturn(true);
        listener = new VarietyListener(plugin);
    }
    @Test void onlyFullyGrownWheatCountsOneBlockInsteadOfDropAmount() {
        Block crop = mock(Block.class); Ageable age = mock(Ageable.class);
        when(crop.getType()).thenReturn(Material.WHEAT); when(crop.getBlockData()).thenReturn(age);
        when(age.getMaximumAge()).thenReturn(7); when(age.getAge()).thenReturn(6);
        listener.harvest(new BlockBreakEvent(crop, player));
        verify(plugin, never()).advance(any(), any(), anySet(), anyDouble());
        when(age.getAge()).thenReturn(7); listener.harvest(new BlockBreakEvent(crop, player));
        verify(plugin).advance(player, Type.HARVEST, Set.of("ANY", "WHEAT"), 1);
    }
    @Test void ripeBerryHarvestCountsButYoungBushDoesNot() {
        var event = mock(PlayerHarvestBlockEvent.class); var block = mock(Block.class); var age = mock(Ageable.class);
        when(event.getPlayer()).thenReturn(player); when(event.getHarvestedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.SWEET_BERRY_BUSH); when(block.getBlockData()).thenReturn(age);
        when(age.getAge()).thenReturn(1); listener.berries(event);
        verify(plugin, never()).advance(any(), any(), anySet(), anyDouble());
        when(age.getAge()).thenReturn(2); listener.berries(event);
        verify(plugin).advance(player, Type.HARVEST, Set.of("ANY", "SWEET_BERRY_BUSH"), 1);
    }
    @Test void completedVillagerTransactionCountsOneExchange() {
        var event = mock(PlayerTradeEvent.class); var villager = mock(Villager.class);
        when(event.getPlayer()).thenReturn(player); when(event.getVillager()).thenReturn(villager);
        when(villager.getType()).thenReturn(EntityType.VILLAGER);
        listener.trade(event);
        verify(plugin).advance(player, Type.TRADE, Set.of("ANY", "VILLAGER"), 1);
    }
    private BrewEvent brewEvent(PotionType potion, boolean owned, boolean unchanged) {
        var event = mock(BrewEvent.class); var block = mock(Block.class); var stand = mock(BrewingStand.class);
        var pdc = mock(PersistentDataContainer.class); var inventory = mock(BrewerInventory.class); var ingredient = mock(ItemStack.class);
        var world = mock(World.class);
        when(event.getBlock()).thenReturn(block); when(block.getState()).thenReturn(stand);
        when(block.getWorld()).thenReturn(world); when(player.getWorld()).thenReturn(world);
        when(stand.getPersistentDataContainer()).thenReturn(pdc);
        when(event.getContents()).thenReturn(inventory); when(inventory.getIngredient()).thenReturn(ingredient);
        when(ingredient.getType()).thenReturn(Material.BLAZE_POWDER);
        if (owned) when(pdc.get(new NamespacedKey(plugin, "brewer"), PersistentDataType.STRING)).thenReturn(QuestEngineTest.ALICE.toString());
        when(pdc.get(new NamespacedKey(plugin, "ingredient"), PersistentDataType.STRING)).thenReturn("BLAZE_POWDER");
        List<ItemStack> results = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            var before = mock(ItemStack.class); var result = mock(ItemStack.class); var meta = mock(PotionMeta.class);
            when(result.getItemMeta()).thenReturn(meta); when(meta.getBasePotionType()).thenReturn(potion);
            when(inventory.getItem(slot)).thenReturn(before); when(before.isSimilar(result)).thenReturn(unchanged); results.add(result);
        }
        when(event.getResults()).thenReturn(results); return event;
    }
    @Test void brewingThreeChangedEffectBottlesCreditsTheIngredientOwner() {
        var event = brewEvent(PotionType.FIRE_RESISTANCE, true, false);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(QuestEngineTest.ALICE)).thenReturn(player);
            listener.brew(event);
        }
        verify(plugin, times(3)).advance(player, Type.BREW, Set.of("ANY", "FIRE_RESISTANCE"), 1);
    }
    @Test void brewingWithoutManualOwnerDoesNotCreditAnyone() {
        listener.brew(brewEvent(PotionType.FIRE_RESISTANCE, false, false));
        verify(plugin, never()).advance(any(), any(), anySet(), anyDouble());
    }
    @Test void awkwardAndUnchangedPotionsDoNotCount() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(QuestEngineTest.ALICE)).thenReturn(player);
            listener.brew(brewEvent(PotionType.AWKWARD, true, false));
            listener.brew(brewEvent(PotionType.FIRE_RESISTANCE, true, true));
        }
        verify(plugin, never()).advance(any(), any(), anySet(), anyDouble());
    }
    private void repair(int beforeDamage, int afterDamage, int playerLevels, boolean consumed) {
        var event = mock(InventoryClickEvent.class); var view = mock(AnvilView.class); var inventory = mock(AnvilInventory.class);
        var before = mock(ItemStack.class); var result = mock(ItemStack.class); var old = mock(Damageable.class); var updated = mock(Damageable.class);
        when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view); when(view.getTopInventory()).thenReturn(inventory);
        when(event.getRawSlot()).thenReturn(2); when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(before.clone()).thenReturn(before); when(before.getType()).thenReturn(Material.IRON_PICKAXE);
        when(before.getItemMeta()).thenReturn(old); when(result.getItemMeta()).thenReturn(updated);
        when(old.getDamage()).thenReturn(beforeDamage); when(updated.getDamage()).thenReturn(afterDamage);
        when(event.getCurrentItem()).thenReturn(result);
        if (consumed) when(inventory.getItem(0)).thenReturn(before, (ItemStack) null); else when(inventory.getItem(0)).thenReturn(before);
        when(inventory.getItem(1)).thenReturn(mock(ItemStack.class));
        when(view.getRepairCost()).thenReturn(3); when(view.getMaximumRepairCost()).thenReturn(40); when(player.getLevel()).thenReturn(playerLevels);
        var scheduler = mock(BukkitScheduler.class);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return mock(BukkitTask.class); })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler); listener.click(event);
        }
    }
    @Test void consumedDamagedInputAndImprovedDurabilityCountAsRepair() {
        repair(100, 25, 10, true);
        verify(plugin).advance(player, Type.ANVIL_REPAIR, Set.of("ANY"), 1);
    }
    @Test void renameInsufficientXpAndUnconsumedInputDoNotCountAsRepair() {
        repair(100, 100, 10, true);
        repair(100, 25, 0, true);
        repair(100, 25, 10, false);
        verify(plugin, never()).advance(any(), any(), anySet(), anyDouble());
    }
}
