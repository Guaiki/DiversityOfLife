package com.evirapo.diversityofcritters.common.guidebook;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which animals each player has registered in the guidebook, kept in the player's persistent data
 * (saved with the player, copied on respawn by {@code GuidebookEvents}).
 */
public final class GuidebookDiscoveries {

    public static final String KEY = "doc_guidebook_discovered";

    public static Set<String> discovered(Player player) {
        Set<String> ids = new LinkedHashSet<>();
        ListTag list = player.getPersistentData().getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) ids.add(list.getString(i));
        return ids;
    }

    /** Registers {@code type} for the player. Returns true if it was not registered yet. */
    public static boolean discover(ServerPlayer player, EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return false;
        CompoundTag data = player.getPersistentData();
        ListTag list = data.getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(id.toString())) return false;
        }
        list.add(StringTag.valueOf(id.toString()));
        data.put(KEY, list);
        return true;
    }

    /** Carries the discoveries from the dead player to the respawned one. */
    public static void copy(Player from, Player to) {
        if (from.getPersistentData().contains(KEY)) {
            to.getPersistentData().put(KEY, from.getPersistentData().getList(KEY, Tag.TAG_STRING).copy());
        }
    }

    private GuidebookDiscoveries() {
    }
}
