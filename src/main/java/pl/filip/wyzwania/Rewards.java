package pl.filip.wyzwania;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.PotionType;
import java.util.*;
import static pl.filip.wyzwania.Model.*;

public final class Rewards {
    private Rewards() {}
    public static List<ItemStack> items(Reward reward) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemSpec spec : reward.items()) {
            Material material = Material.valueOf(spec.material());
            ItemStack item = new ItemStack(material, spec.amount());
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta book && !spec.enchantment().isBlank()) {
                var enchantment = Registry.ENCHANTMENT.get(Objects.requireNonNull(NamespacedKey.fromString(spec.enchantment())));
                book.addStoredEnchant(Objects.requireNonNull(enchantment), spec.level(), false);
            }
            if (meta instanceof PotionMeta potion && !spec.potion().isBlank()) potion.setBasePotionType(PotionType.valueOf(spec.potion()));
            item.setItemMeta(meta); result.add(item);
        }
        return result;
    }
    /** Simulate the entire bundle in storage slots; never partially issue a reward. */
    public static boolean fits(ItemStack[] storage, List<ItemStack> rewards) {
        ItemStack[] copy = Arrays.stream(storage).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        for (ItemStack item : rewards) {
            int left = item.getAmount();
            for (ItemStack slot : copy) {
                if (slot != null && slot.isSimilar(item)) {
                    int moved = Math.min(left, Math.max(0, Math.min(64, slot.getMaxStackSize()) - slot.getAmount()));
                    slot.setAmount(slot.getAmount() + moved); left -= moved;
                }
            }
            for (int i = 0; i < copy.length && left > 0; i++) {
                if (copy[i] == null || copy[i].getType().isAir()) {
                    int moved = Math.min(left, Math.min(64, item.getMaxStackSize()));
                    copy[i] = item.clone(); copy[i].setAmount(moved); left -= moved;
                }
            }
            if (left > 0) return false;
        }
        return true;
    }
}
