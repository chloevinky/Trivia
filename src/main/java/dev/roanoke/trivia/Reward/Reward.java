package dev.roanoke.trivia.Reward;

import dev.roanoke.trivia.Trivia;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class Reward {

    public String itemName;
    public String itemDisplayName;
    public Integer quantity;
    public ItemStack itemStack;

    public Reward(String itemName, String itemDisplayName, Integer quantity) {
        this.itemName = itemName;
        this.itemDisplayName = itemDisplayName;
        this.quantity = quantity;
        this.itemStack = buildItemStack(itemName, quantity);

        if (this.itemStack == null) {
            Trivia.LOGGER.warn("Reward item not registered: " + itemName + " (display name '" + itemDisplayName + "')");
        }
    }

    private static ItemStack buildItemStack(String itemName, Integer quantity) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        Identifier id = Identifier.tryParse(itemName);
        if (id == null) {
            return null;
        }
        if (!Registries.ITEM.containsId(id)) {
            return null;
        }
        if (Registries.ITEM.get(id) == Items.AIR) {
            return null;
        }
        int qty = (quantity == null || quantity < 1) ? 1 : quantity;
        return new ItemStack(Registries.ITEM.get(id), qty);
    }
}
