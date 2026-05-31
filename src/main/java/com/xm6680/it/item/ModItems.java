package com.xm6680.it.item;

import com.xm6680.it.ItMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.function.Function;

public final class ModItems {
    public static final Item RECEIVER = register("receiver", key -> new ReceiverItem(new Item.Settings()
            .registryKey(key)
            .maxCount(1)
            .rarity(Rarity.UNCOMMON)));

    private ModItems() {
    }

    public static void register() {
    }

    private static Item register(String name, Function<RegistryKey<Item>, Item> factory) {
        Identifier id = Identifier.of(ItMod.MOD_ID, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return Registry.register(Registries.ITEM, key, factory.apply(key));
    }
}
