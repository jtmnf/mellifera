package com.joaonf.mellifera.item;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.joaonf.mellifera.block.HiveBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/// Points at the nearest wild beehive.
///
/// Rides on vanilla's own compass machinery rather than reimplementing it: the needle angle
/// comes from the `minecraft:compass` model property with `target: "lodestone"`, which reads
/// DataComponents.LODESTONE_TRACKER off whatever stack it is drawn on, regardless of which
/// item that is. So the entire client side of this feature is the item model JSON, and all
/// this class does is keep that component pointed somewhere sensible.
///
/// The target is re-checked every tick against the block actually standing there, which is
/// what stops the needle following a hive the player has already broken -- vanilla's
/// LodestoneTracker.tick does the same job for lodestones by asking the POI manager, but a
/// hive is not a POI, so the check is done here instead. Breaking the hive you are walking
/// towards makes the needle move on to the next one rather than march you to an empty patch
/// of forest.
public class BeeLocatorItem extends Item {
    /// Ticks between automatic scans.
    ///
    /// Deliberately slow, because the common case is the expensive one: standing anywhere
    /// with no hive in range means there is no target to validate, so the automatic path
    /// re-scans forever rather than occasionally. Right-clicking gives an immediate refresh
    /// whenever the player actually wants one, which is what buys the room to back this off.
    private static final int SCAN_INTERVAL_TICKS = 60;

    /// Ticks the manual refresh is locked out for. Enforced on the server as well as
    /// declared to the client: ItemCooldowns is only consulted client-side (see
    /// MultiPlayerGameMode), so a cooldown that lives solely in the component would leave a
    /// full chunk sweep one packet away from being spammed.
    private static final int REFRESH_COOLDOWN_TICKS = 200;

    /// How far out to look, in chunks. Bounded by what is loaded anyway -- see scan().
    private static final int SCAN_CHUNK_RADIUS = 12;

    /// Blocks along one edge of a chunk, for the ring bound in scan().
    private static final int CHUNK_BLOCKS = 16;

    public BeeLocatorItem(Properties properties) {
        super(properties);
    }

    /// Right-click: look again, and take the nearest hive found now.
    ///
    /// The automatic scan only fires when the needle has nothing valid to point at, so
    /// without this the locator latches onto the first hive it ever sees and keeps pointing
    /// there even once the player has walked past three closer ones. This is the "actually,
    /// what is nearest *now*" button, and re-pointing at something nearer is the whole
    /// reason it exists -- so unlike the automatic path it runs even when the current target
    /// is still perfectly valid.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // The client is told to swing and nothing else; it has no chunk data to search and
        // the component it draws the needle from arrives from the server anyway.
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }

        player.getCooldowns().addCooldown(stack, REFRESH_COOLDOWN_TICKS);

        BlockPos found = scan(serverLevel, player.blockPosition());
        if (found == null) {
            // Cleared rather than left alone: the needle must never keep pointing somewhere
            // the last look could not confirm.
            stack.remove(DataComponents.LODESTONE_TRACKER);
            overlay(player, Component.translatable("item.mellifera.bee_locator.none"));
            return InteractionResult.SUCCESS;
        }

        stack.set(DataComponents.LODESTONE_TRACKER,
            new LodestoneTracker(Optional.of(GlobalPos.of(serverLevel.dimension(), found)), true));

        overlay(player, Component.translatable("item.mellifera.bee_locator.found",
            Mth.floor(Math.sqrt(found.distSqr(player.blockPosition())))));
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        BlockPos current = trackedPos(stack, level);
        if (current != null && isHive(level, current)) {
            return;
        }

        // The hive it was pointing at is gone (or there never was one). Clear first, so a
        // scan that finds nothing leaves the needle spinning rather than still aimed at a
        // hive that no longer exists.
        if (current != null) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
        }

        if (level.getGameTime() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        BlockPos found = scan(level, owner.blockPosition());
        if (found != null) {
            stack.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), found)), true));
        }
    }

    /// Over the hotbar rather than in chat: this is a reading, not a conversation, and a
    /// player pressing the button repeatedly should not be scrolling their chat log away.
    private static void overlay(Player player, Component text) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(text, true);
        }
    }

    /// Where this stack is currently pointing, or null if it is pointing nowhere or into
    /// another dimension -- a target in the Nether is not something this level can validate,
    /// and the compass model already refuses to point across dimensions.
    private static @Nullable BlockPos trackedPos(ItemStack stack, ServerLevel level) {
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker == null) {
            return null;
        }

        GlobalPos target = tracker.target().orElse(null);
        return target == null || target.dimension() != level.dimension() ? null : target.pos();
    }

    private static boolean isHive(ServerLevel level, BlockPos pos) {
        // isLoaded, not just getBlockState: an unloaded chunk reads back as air, which would
        // look exactly like the hive having been broken and would throw away a perfectly good
        // target the moment the player walked out of range of it.
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof HiveBlock;
    }

    /// Nearest hive in the loaded chunks around `origin`, or null.
    ///
    /// Only loaded chunks, deliberately. Reaching further would mean generating or reading
    /// chunks off disk from an inventory tick, which is exactly the kind of thing that turns
    /// a convenience item into a server stall. The practical effect is that this finds the
    /// hive you are about to walk past and would otherwise miss in the foliage, rather than
    /// one a thousand blocks away.
    private static @Nullable BlockPos scan(ServerLevel level, BlockPos origin) {
        int centreX = SectionPos.blockToSectionCoord(origin.getX());
        int centreZ = SectionPos.blockToSectionCoord(origin.getZ());
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        // Ring by ring outward, so the answer can be known before the whole box has been read.
        //
        // What this wants is the *nearest* hive, and a chunk at ring k cannot hold a block closer
        // than (k-1) chunks: the player may stand on their own chunk's edge, but no nearer than
        // that. So once a ring has finished and the best hive found is closer than everything the
        // remaining rings could possibly hold, the rest of the box cannot change the answer and is
        // not read. A hive anywhere nearby -- the common case, and the case worth being cheap --
        // now costs a couple of rings instead of all six hundred and twenty-five chunks.
        for (int ring = 0; ring <= SCAN_CHUNK_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // The perimeter only; the inside of this square was walked by earlier rings.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }

                    int chunkX = centreX + dx;
                    int chunkZ = centreZ + dz;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }

                    BlockPos hive = findInChunk(chunk, chunkX, chunkZ, origin);
                    if (hive == null) {
                        continue;
                    }

                    double distance = hive.distSqr(origin);
                    if (distance < bestDistance) {
                        best = hive;
                        bestDistance = distance;
                    }
                }
            }

            // Squared, like distSqr, and horizontal, which keeps it a genuine lower bound: the
            // vertical leg only ever makes the real distance longer.
            double reach = (double) ring * CHUNK_BLOCKS;
            if (best != null && bestDistance <= reach * reach) {
                break;
            }
        }

        return best;
    }

    /// The palette test is what makes scanning a chunk affordable.
    ///
    /// LevelChunkSection.maybeHas asks the section's own block-state palette whether it could
    /// contain a match, which is a walk of a handful of entries rather than of 4096 blocks.
    /// Hives are roughly one per two hundred chunks, so almost every section answers no and
    /// costs nothing; only the rare section that says maybe is then read block by block.
    private static @Nullable BlockPos findInChunk(LevelChunk chunk, int chunkX, int chunkZ, BlockPos origin) {
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof HiveBlock)) {
                continue;
            }

            int baseX = SectionPos.sectionToBlockCoord(chunkX);
            int baseZ = SectionPos.sectionToBlockCoord(chunkZ);
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));

            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!(section.getBlockState(x, y, z).getBlock() instanceof HiveBlock)) {
                            continue;
                        }

                        cursor.set(baseX + x, baseY + y, baseZ + z);
                        double distance = cursor.distSqr(origin);
                        if (distance < bestDistance) {
                            best = cursor.immutable();
                            bestDistance = distance;
                        }
                    }
                }
            }
        }

        return best;
    }
}
