package io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock;

import io.th0rgal.oraxen.api.OraxenBlocks;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Iterator;

/**
 * Listener to handle StructureGrowEvent to prevent Oraxen NoteBlocks 
 * from being converted to vanilla blocks when trees grow nearby.
 * 
 * This fixes the issue where growing trees (especially spruce trees that change
 * dirt to podzol) would overwrite custom Oraxen blocks with vanilla blocks.
 */
public class NoteBlockStructureGrowListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        // Use iterator to safely remove elements while iterating
        Iterator<BlockState> iterator = event.getBlocks().iterator();
        
        while (iterator.hasNext()) {
            BlockState blockState = iterator.next();
            Block block = blockState.getBlock();
            
            // If this block is currently an Oraxen NoteBlock, preserve it
            if (block.getType() == Material.NOTE_BLOCK && OraxenBlocks.isOraxenNoteBlock(block)) {
                // Get the current Oraxen block data to verify it's a custom block
                NoteBlockMechanic mechanic = OraxenBlocks.getNoteBlockMechanic(block);
                if (mechanic != null) {
                    // Remove this block from the structure growth changes
                    // This prevents the tree growth from overwriting our custom block
                    iterator.remove();
                }
            }
        }
    }
}