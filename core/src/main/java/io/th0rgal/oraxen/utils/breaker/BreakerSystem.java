package io.th0rgal.oraxen.utils.breaker;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureDamageEvent;
import io.th0rgal.oraxen.api.events.noteblock.OraxenNoteBlockDamageEvent;
import io.th0rgal.oraxen.api.events.stringblock.OraxenStringBlockDamageEvent;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock.NoteBlockMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.stringblock.StringBlockMechanic;
import io.th0rgal.oraxen.utils.BlockHelpers;
import io.th0rgal.oraxen.utils.EventUtils;
import io.th0rgal.oraxen.utils.ItemUtils;
import io.th0rgal.oraxen.utils.PotionUtils;
import io.th0rgal.oraxen.utils.blocksounds.BlockSounds;
import io.th0rgal.oraxen.utils.drops.Drop;
import io.th0rgal.oraxen.utils.wrappers.EnchantmentWrapper;
import io.th0rgal.protectionlib.ProtectionLib;
import org.bukkit.*;

import java.lang.reflect.Method;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanicFactory.getBlockMechanic;

public class BreakerSystem {

    public static final List<HardnessModifier> MODIFIERS = new ArrayList<>();
    private final Map<Location, fr.euphyllia.energie.model.Scheduler> breakerPerLocation = new HashMap<>();
    private final Map<Location, fr.euphyllia.energie.model.SchedulerTaskInter> breakerPlaySound = new HashMap<>();
    private final PacketAdapter listener = new PacketAdapter(OraxenPlugin.get(),
            ListenerPriority.LOW, PacketType.Play.Client.BLOCK_DIG) {
        @Override
        public void onPacketReceiving(final PacketEvent event) {
            final PacketContainer packet = event.getPacket();
            final Player player = event.getPlayer();
            final ItemStack item = player.getInventory().getItemInMainHand();
            if (player.getGameMode() == GameMode.CREATIVE) return;

            final StructureModifier<BlockPosition> dataTemp = packet.getBlockPositionModifier();
            final StructureModifier<EnumWrappers.Direction> dataDirection = packet.getDirections();
            final StructureModifier<EnumWrappers.PlayerDigType> data = packet
                    .getEnumModifier(EnumWrappers.PlayerDigType.class, 2);
            EnumWrappers.PlayerDigType tempType;
            try {
                tempType = data.getValues().getFirst();
            } catch (IllegalArgumentException exception) {
                tempType = EnumWrappers.PlayerDigType.SWAP_HELD_ITEMS;
            } final EnumWrappers.PlayerDigType type = tempType;

            final BlockPosition pos = dataTemp.getValues().getFirst();
            // Hyping Start - Restrict packet distance to player
            Location pLoc = player.getLocation();
            if (pos.toVector().distanceSquared(pLoc.toVector()) > (10 * 10)) return;
            // Hyping End
            final World world = player.getWorld();
            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return; // Hyping - Check chunk loaded
            final Block block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            final Location location = block.getLocation();
            final BlockFace blockFace = dataDirection.size() > 0 ?
                    BlockFace.valueOf(dataDirection.read(0).name()) :
                    BlockFace.UP; Bukkit.getRegionScheduler().execute(OraxenPlugin.get(), location, () -> {

            HardnessModifier triggeredModifier = null;
            for (final HardnessModifier modifier : MODIFIERS) {
                if (modifier.isTriggered(player, block, item)) {
                    triggeredModifier = modifier;
                    break;
                }
            }
            if (triggeredModifier == null) return;
            final long period = triggeredModifier.getPeriod(player, block, item);
            if (period == 0) return;

            NoteBlockMechanic noteMechanic = OraxenBlocks.getNoteBlockMechanic(block);
            StringBlockMechanic stringMechanic = OraxenBlocks.getStringMechanic(block);
            FurnitureMechanic furnitureMechanic = OraxenFurniture.getFurnitureMechanic(block);
            if (block.getType() == Material.NOTE_BLOCK && noteMechanic == null) return;
            if (block.getType() == Material.TRIPWIRE && stringMechanic == null) return;
            if (block.getType() == Material.BARRIER && furnitureMechanic == null) return;

            event.setCancelled(true);

            if (type == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK) {
                // Get these when block is started being broken to minimize checks & allow for proper damage checks later
                final Drop drop;
                if (furnitureMechanic != null)
                    drop = furnitureMechanic.getDrop() != null ? furnitureMechanic.getDrop() : Drop.emptyDrop();
                else if (noteMechanic != null)
                    drop = noteMechanic.getDrop() != null ? noteMechanic.getDrop() : Drop.emptyDrop();
                else if (stringMechanic != null)
                    drop = stringMechanic.getDrop() != null ? stringMechanic.getDrop() : Drop.emptyDrop();
                else drop = null;

                OraxenPlugin.getScheduler().runTask(fr.euphyllia.energie.model.SchedulerType.SYNC, player, playerTask ->
                        player.addPotionEffect(new PotionEffect(PotionUtils.getEffectType("mining_fatigue"),
                                (int) (period * 11),
                                Integer.MAX_VALUE,
                                false, false, false)), null);

                if (breakerPerLocation.containsKey(location))
                    breakerPerLocation.get(location).cancelAllTask();

                final fr.euphyllia.energie.model.Scheduler scheduler = OraxenPlugin.getScheduler();
                // Cancellation state is being ignored.
                // However still needs to be called for plugin support.
                final PlayerInteractEvent playerInteractEvent =
                        new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), block, blockFace, EquipmentSlot.HAND);
                scheduler.runTask(fr.euphyllia.energie.model.SchedulerType.SYNC, player, task -> Bukkit.getPluginManager().callEvent(playerInteractEvent), null);

                breakerPerLocation.put(location, scheduler);

                // If the relevant damage event is cancelled, return
                if (blockDamageEventCancelled(block, player)) {
                    stopBlockBreaker(location);
                    return;
                }

                // Methods for sending multi-barrier block-breaks
                final List<Location> furnitureBarrierLocations = furnitureBarrierLocations(furnitureMechanic, block);
                final HardnessModifier modifier = triggeredModifier;
                startBlockHitSound(location); java.util.concurrent.atomic.AtomicInteger value = new java.util.concurrent.atomic.AtomicInteger(0);

                scheduler.runAtFixedRate(fr.euphyllia.energie.model.SchedulerType.SYNC, location, bukkitTask -> { if (bukkitTask == null) return;
                    //int value = 0;

                    //@Override
                    //public void accept(final BukkitTask bukkitTask) {
                        if (!breakerPerLocation.containsKey(location)) {
                            bukkitTask.cancel();
                            stopBlockHitSound(location);
                            return;
                        }

                        if (item.getEnchantmentLevel(EnchantmentWrapper.EFFICIENCY) >= 5)
                            value.set(10);

                        for (final Entity entity : world.getNearbyEntities(location, 16, 16, 16)) {
                            if (entity instanceof Player viewer) {
                                if (furnitureMechanic != null) for (Location barrierLoc : furnitureBarrierLocations)
                                    sendBlockBreak(viewer, barrierLoc, value.get());
                                else sendBlockBreak(viewer, location, value.get());
                            }
                        }

                        if (value.getAndIncrement() < 10) return;
                        Location permLoc = location;
                        Entity base = null;
                        if (furnitureMechanic != null) {
                            base = furnitureMechanic.getBaseEntity(block);
                            if (base != null) permLoc = base.getLocation();
                        }
                        boolean allowed = ProtectionLib.canBreak(player, permLoc);
                        if (!allowed && isHypingFieldsTagged(base)) {
                            allowed = true;
                        }
                        if (EventUtils.callEvent(new BlockBreakEvent(block, player)) && allowed) {
                            // Damage item with properties identified earlier
                            ItemUtils.damageItem(player, drop, item);
                            modifier.breakBlock(player, block, item);
                        } else stopBlockHitSound(location);

                        OraxenPlugin.getScheduler().runTask(fr.euphyllia.energie.model.SchedulerType.SYNC, player, task ->
                                player.removePotionEffect(PotionUtils.getEffectType("mining_fatigue")), null);

                        stopBlockBreaker(location);
                        stopBlockHitSound(location);
                        for (final Entity entity : world.getNearbyEntities(location, 16, 16, 16)) {
                            if (entity instanceof Player viewer) {
                                if (furnitureMechanic != null) for (Location barrierLoc : furnitureBarrierLocations)
                                    sendBlockBreak(viewer, barrierLoc, value.get());
                                else sendBlockBreak(viewer, location, value.get());
                            }
                        }
                        bukkitTask.cancel();
                    //}
                }, period, period);
            } else {
                OraxenPlugin.getScheduler().runTask(fr.euphyllia.energie.model.SchedulerType.SYNC, player, playerTask -> {
                    player.removePotionEffect(PotionUtils.getEffectType("mining_fatigue"));
                    Location permLoc2 = location;
                    FurnitureMechanic furnMech2 = OraxenFurniture.getFurnitureMechanic(block);
                    Entity base2 = null;
                    if (furnMech2 != null) {
                        base2 = furnMech2.getBaseEntity(block);
                        if (base2 != null) permLoc2 = base2.getLocation();
                    }
                    boolean allowed2 = ProtectionLib.canBreak(player, permLoc2);
                    if (!allowed2 && isHypingFieldsTagged(base2)) {
                        allowed2 = true;
                    }
                    if (!allowed2)
                        player.sendBlockChange(location, block.getBlockData());

                    for (final Entity entity : world.getNearbyEntities(location, 16, 16, 16))
                        if (entity instanceof Player viewer)
                            sendBlockBreak(viewer, location, 10);
                    stopBlockBreaker(location);
                    stopBlockHitSound(location);
                }, null);
            }});
        }
    };

    private List<Location> furnitureBarrierLocations(FurnitureMechanic furnitureMechanic, Block block) {
        fr.euphyllia.energie.model.Scheduler scheduler = breakerPerLocation.get(block.getLocation());
        if (scheduler == null) return List.of(block.getLocation());

        AtomicReference<Entity> furnitureBaseEntity = new AtomicReference<>();
        Entity entity = furnitureMechanic != null ? furnitureMechanic.getBaseEntity(block) : null;
        if (entity != null) {
            scheduler.runTask(fr.euphyllia.energie.model.SchedulerType.SYNC, entity, task -> furnitureBaseEntity.set(entity), null);
        }
        return furnitureMechanic != null && furnitureBaseEntity.get() != null
                ? furnitureMechanic.getLocations(FurnitureMechanic.getFurnitureYaw(furnitureBaseEntity.get()),
                furnitureBaseEntity.get().getLocation(), furnitureMechanic.getBarriers())
                : Collections.singletonList(block.getLocation());
    }

    private boolean blockDamageEventCancelled(Block block, Player player) {
        fr.euphyllia.energie.model.Scheduler scheduler = breakerPerLocation.get(block.getLocation());
        if (scheduler == null) return false;

        switch (block.getType()) {
            case NOTE_BLOCK -> {
                NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(block);
                if (mechanic == null) return true;
                OraxenNoteBlockDamageEvent event = new OraxenNoteBlockDamageEvent(mechanic, block, player);
                scheduler.runTask(fr.euphyllia.energie.model.SchedulerType.SYNC, player, (schedulerTaskInter) -> Bukkit.getPluginManager().callEvent(event), null);
                return event.isCancelled();
            }
            case TRIPWIRE -> {
                StringBlockMechanic mechanic = OraxenBlocks.getStringMechanic(block);
                if (mechanic == null) return true;
                OraxenStringBlockDamageEvent event = new OraxenStringBlockDamageEvent(mechanic, block, player);
                scheduler.runTask(fr.euphyllia.energie.model.SchedulerType.SYNC, player, (schedulerTaskInter) -> Bukkit.getPluginManager().callEvent(event), null);
                return event.isCancelled();
            }
            case BARRIER -> {
                try {
                    return barrierDamageEventCancelled(block, player);
                } catch (Exception e) {
                    return false;
                }
            }
            case BEDROCK -> { // For BedrockBreakMechanic
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    private boolean barrierDamageEventCancelled(Block block, Player player) {
        FurnitureMechanic mechanic = OraxenFurniture.getFurnitureMechanic(block);
        if (mechanic == null) {
            return true;
        }
        Entity baseEntity = mechanic.getBaseEntity(block);
        if (baseEntity == null) {
            return true;
        }
        OraxenFurnitureDamageEvent event = new OraxenFurnitureDamageEvent(mechanic, baseEntity, player, block);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    private void sendBlockBreak(final Player player, final Location location, final int stage) {
        final PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
        packet.getIntegers().write(0, location.hashCode()).write(1, stage);
        packet.getBlockPositionModifier().write(0, new BlockPosition(location.toVector()));

        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    }

    private void stopBlockBreaker(Location location) {
        if (breakerPerLocation.containsKey(location)) {
            breakerPerLocation.get(location).cancelAllTask();
            breakerPerLocation.remove(location);
        }
    }

    private void startBlockHitSound(Location location) {
        fr.euphyllia.energie.model.Scheduler scheduler = breakerPerLocation.get(location);
        BlockSounds blockSounds = getBlockSounds(location.getBlock());

        if (scheduler == null || blockSounds == null || !blockSounds.hasHitSound()) {
            stopBlockHitSound(location);
            return;
        }

        breakerPlaySound.put(location, scheduler.runAtFixedRate(fr.euphyllia.energie.model.SchedulerType.SYNC, location,
                (schedulerTaskInter) -> BlockHelpers.playCustomBlockSound(location, getHitSound(location.getBlock()), blockSounds.getHitVolume(), blockSounds.getHitPitch())
                , 1L, 4L));
    }

    private void stopBlockHitSound(Location location) {
        fr.euphyllia.energie.model.SchedulerTaskInter value = breakerPlaySound.get(location);
        if (value != null) value.cancel();
        breakerPlaySound.remove(location);
    }

    // Lightweight reflection-based check to detect HypingFields Oraxen crops without a hard dependency
    private boolean isHypingFieldsTagged(Entity entity) {
        if (entity == null) return false;
        try {
            NamespacedKey key = new NamespacedKey("hypingfields", "hfields_crop");
            return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isHypingFieldsOraxenCropAt(Location location) {
        if (location == null) return false;
        try {
            Class<?> fmClass = Class.forName("fr.kotlini.hypingfields.manager.FieldsManager");
            Method getInstance = fmClass.getMethod("getInstance");
            Object fm = getInstance.invoke(null);
            Method getFieldByLocation = fmClass.getMethod("getFieldByLocation", org.bukkit.Location.class);
            Object field = getFieldByLocation.invoke(fm, location);
            if (field == null) return false;
            Class<?> locUtils = Class.forName("fr.kotlini.hypingfields.util.LocationUtils");
            Method locationToLong = locUtils.getMethod("locationToLong", org.bukkit.Location.class);
            long key = ((Number) locationToLong.invoke(null, location)).longValue();
            Method locationsMap = field.getClass().getMethod("locationsMap");
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) locationsMap.invoke(field);
            Object plant = map.get(key);
            if (plant == null) return false;
            Method getCropType = plant.getClass().getMethod("getCropType");
            Object cropType = getCropType.invoke(plant);
            Class<?> cropTypeClass = Class.forName("fr.kotlini.hypingfields.model.CropType");
            Object oraxen = cropTypeClass.getField("ORAXEN_FURNITURE_CROP").get(null);
            return cropType == oraxen || (cropType != null && cropType.equals(oraxen));
        } catch (Throwable t) {
            return false;
        }
    }

    public void registerListener() {
        ProtocolLibrary.getProtocolManager().addPacketListener(listener);
    }

    private BlockSounds getBlockSounds(Block block) {
        ConfigurationSection soundSection = OraxenPlugin.get().getConfigsManager().getMechanics().getConfigurationSection("custom_block_sounds");
        if (soundSection == null) return null;
        switch (block.getType()) {
            case NOTE_BLOCK -> {
                NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(block);
                if (mechanic == null || !mechanic.hasBlockSounds()) return null;
                if (!soundSection.getBoolean("noteblock_and_block")) return null;
                else return mechanic.getBlockSounds();
            }
            case MUSHROOM_STEM -> {
                BlockMechanic mechanic = getBlockMechanic(block);
                if (mechanic == null || !mechanic.hasBlockSounds()) return null;
                if (!soundSection.getBoolean("noteblock_and_block")) return null;
                else return mechanic.getBlockSounds();
            }
            case TRIPWIRE -> {
                StringBlockMechanic mechanic = OraxenBlocks.getStringMechanic(block);
                if (mechanic == null || !mechanic.hasBlockSounds()) return null;
                if (!soundSection.getBoolean("stringblock_and_furniture")) return null;
                else return mechanic.getBlockSounds();
            }
            case BARRIER -> {
                FurnitureMechanic mechanic = OraxenFurniture.getFurnitureMechanic(block);
                if (mechanic == null || !mechanic.hasBlockSounds()) return null;
                if (!soundSection.getBoolean("stringblock_and_furniture")) return null;
                else return mechanic.getBlockSounds();
            }
            default -> {
                return null;
            }
        }
    }

    private String getHitSound(Block block) {
        ConfigurationSection soundSection = OraxenPlugin.get().getConfigsManager().getMechanics().getConfigurationSection("custom_block_sounds");
        if (soundSection == null) return null;
        BlockSounds sounds = getBlockSounds(block);
        if (sounds == null) return null;
        return switch (block.getType()) {
            case NOTE_BLOCK, MUSHROOM_STEM -> sounds.hasHitSound() ? sounds.getHitSound() : "required.wood.hit";
            case TRIPWIRE -> sounds.hasHitSound() ? sounds.getHitSound() : "block.tripwire.detach";
            case BARRIER -> sounds.hasHitSound() ? sounds.getHitSound() : "required.stone.hit";
            default -> block.getBlockData().getSoundGroup().getHitSound().getKey().toString();
        };
    }
}