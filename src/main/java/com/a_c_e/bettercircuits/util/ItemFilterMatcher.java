package com.a_c_e.bettercircuits.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

//Shared "does this item match the filter" rule for both Filtered Hopper (block) and Filtered Hopper Minecart -
//exact item type plus every data component EXCEPT the ones that track something other than "what kind of item is
//this": current durability (DAMAGE) and whatever a container-like item happens to be holding right now
//(CONTAINER/CONTAINER_LOOT for shulker boxes, BUNDLE_CONTENTS for bundles) - matches the user's own spec exactly
//(a Fortune III book filters differently than Fortune II, but not by durability or shulker contents).
public final class ItemFilterMatcher {
    private ItemFilterMatcher() {
    }

    public static boolean matches(ItemStack candidate, ItemStack filter) {
        if (candidate.getItem() != filter.getItem()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(stripIdentityIrrelevant(candidate), stripIdentityIrrelevant(filter));
    }

    private static ItemStack stripIdentityIrrelevant(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(DataComponents.DAMAGE);
        copy.remove(DataComponents.CONTAINER);
        copy.remove(DataComponents.CONTAINER_LOOT);
        copy.remove(DataComponents.BUNDLE_CONTENTS);
        return copy;
    }
}
