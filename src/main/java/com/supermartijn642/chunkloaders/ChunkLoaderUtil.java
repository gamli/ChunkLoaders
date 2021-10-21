package com.supermartijn642.chunkloaders;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.data.*;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.LongArrayNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created 8/18/2020 by SuperMartijn642
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkLoaderUtil {

    @CapabilityInject(ChunkTracker.class)
    public static Capability<ChunkTracker> TRACKER_CAPABILITY;

    public static void register() {
        CapabilityManager.INSTANCE.register(ChunkTracker.class, new Capability.IStorage<ChunkTracker>() {
            public CompoundNBT writeNBT(Capability<ChunkTracker> capability, ChunkTracker instance, Direction side) {
                return instance.write();
            }

            public void readNBT(Capability<ChunkTracker> capability, ChunkTracker instance, Direction side, INBT nbt) {
                instance.read((CompoundNBT) nbt);
            }
        }, ChunkTracker::new);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<World> e) {

        World world = e.getObject();

        if (world.isClientSide || !(world instanceof ServerWorld)) {
            return;
        }

        LazyOptional<ChunkTracker> tracker = LazyOptional.of(() -> new ChunkTracker((ServerWorld) world));
        e.addCapability(new ResourceLocation("chunkloaders", "chunk_tracker"), new ICapabilitySerializable<INBT>() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                return cap == TRACKER_CAPABILITY ? tracker.cast() : LazyOptional.empty();
            }

            @Override
            public INBT serializeNBT() {
                return TRACKER_CAPABILITY.writeNBT(tracker.orElse(null), null);
            }

            @Override
            public void deserializeNBT(INBT nbt) {
                TRACKER_CAPABILITY.readNBT(tracker.orElse(null), null, nbt);
            }
        });
        e.addListener(tracker::invalidate);
    }

    public static class ChunkTracker {

        private final ServerWorld world;
        private final Map<ChunkPos, TrackedChunk> chunks = new HashMap<>();

        public ChunkTracker(ServerWorld world) {
            this.world = world;
        }

        public ChunkTracker() {
            this.world = null;
        }

        public void add(ChunkPos chunk, BlockPos loader, UUID teamId, UUID playerId) {

            // we only support loaders that are initially placed by a player
            if (teamId == null || playerId == null) {
                return;
            }
            if (this.chunks.containsKey(chunk) && this.chunks.get(chunk).loaders.contains(loader)) {
                return;
            }
            if (this.world == null) {
                return;
            }

            ImmutableTriple<FTBChunksTeamData, PlayerEntity, ChunkDimPos> teamPlayerChunk =
                    teamDataPlayerChunkDimPos(chunk, teamId, playerId);

            FTBChunksTeamData teamData = teamPlayerChunk.left;
            PlayerEntity player = teamPlayerChunk.middle;
            CommandSource commandSource = player.createCommandSourceStack();
            ChunkDimPos chunkDimPos = teamPlayerChunk.right;

            if (!this.chunks.containsKey(chunk)) {
                TrackedChunk newTrackedChunk = new TrackedChunk();
                this.chunks.put(chunk, newTrackedChunk);
                ClaimResult claimResult = teamData.claim(commandSource, chunkDimPos, false);
                newTrackedChunk.isClaimedByTracker = claimResult.isSuccess();
                if (newTrackedChunk.isClaimedByTracker || claimResult == ClaimResults.ALREADY_CLAIMED) {
                    ClaimResult loadResult = teamData.load(commandSource, chunkDimPos, false);
                    newTrackedChunk.isForceLoadedByTracker = loadResult.isSuccess();
                    if (!newTrackedChunk.isForceLoadedByTracker && loadResult != ClaimResults.ALREADY_LOADED) {
                        String message = "Could not force load chunk - reason: " + loadResult;
                        logError(message);
                        player.displayClientMessage(new StringTextComponent(message).withStyle(TextFormatting.RED), false);
                    }
                } else {
                    String message = "Could not claim chunk - reason: " + claimResult;
                    logError(message);
                    player.displayClientMessage(new StringTextComponent(message).withStyle(TextFormatting.RED), false);
                }
            }

            this.chunks.get(chunk).loaders.add(loader);
        }

        public void remove(ChunkPos chunk, BlockPos loader, UUID teamId, UUID playerId) {

            if (teamId == null || playerId == null) {
                return;
            }
            if (!this.chunks.containsKey(chunk) || !this.chunks.get(chunk).loaders.contains(loader)) {
                return;
            }
            if (this.world == null) {
                return;
            }

            TrackedChunk trackedChunk = this.chunks.get(chunk);
            trackedChunk.loaders.remove(loader);

            if (trackedChunk.loaders.size() == 0) {

                this.chunks.remove(chunk);

                ImmutableTriple<FTBChunksTeamData, PlayerEntity, ChunkDimPos> teamPlayerChunk =
                        teamDataPlayerChunkDimPos(chunk, teamId, playerId);

                FTBChunksTeamData teamData = teamPlayerChunk.left;
                PlayerEntity player = teamPlayerChunk.middle;
                CommandSource commandSource = player.createCommandSourceStack();
                ChunkDimPos chunkDimPos = teamPlayerChunk.right;

                if (trackedChunk.isForceLoadedByTracker) {
                    ClaimResult unloadResult = teamData.unload(commandSource, chunkDimPos, false);
                    if (!unloadResult.isSuccess() && unloadResult != ClaimResults.NOT_LOADED) {
                        String message = "tracker.remove() Chunk could not be unloaded - reason: " + unloadResult + ", chunk: " + chunkDimPos + ", loader: " + loader;
                        logError(message);
                        player.displayClientMessage(new StringTextComponent(message).withStyle(TextFormatting.RED), false);
                    }
                }

                if (trackedChunk.isClaimedByTracker) {
                    ClaimResult unclaimResult = teamData.unclaim(commandSource, chunkDimPos, false);
                    if (!unclaimResult.isSuccess() && unclaimResult != ClaimResults.NOT_CLAIMED) {
                        String message = "tracker.remove() Chunk could not be unclaimed - reason: " + unclaimResult + ", chunk: " + chunkDimPos + ", loader: " + loader;
                        logError(message);
                        player.displayClientMessage(new StringTextComponent(message).withStyle(TextFormatting.RED), false);
                    }
                }
            }
        }

        private ImmutableTriple<FTBChunksTeamData, PlayerEntity, ChunkDimPos> teamDataPlayerChunkDimPos(
                ChunkPos chunk, UUID teamId, UUID playerId) {

            PlayerEntity player = this.world.getPlayerByUUID(playerId);
            if (player == null) {
                player = new FakePlayer(this.world, new GameProfile(playerId, "FAKE-" + playerId));
            }

            return new ImmutableTriple(
                    FTBChunksAPI.getManager().getData(FTBTeamsAPI.getPlayerTeam(teamId)),
                    player,
                    XZ.of(chunk).dim(this.world));
        }


        public void moveLoader(BlockPos loaderFrom, BlockPos loaderTo) {
            this.chunks.values().forEach(trackedChunk -> {
                if (trackedChunk.loaders.remove(loaderFrom)) {
                    trackedChunk.loaders.add(loaderTo);
                }
            });
        }

        public CompoundNBT write() {

            CompoundNBT compound = new CompoundNBT();

            for (Map.Entry<ChunkPos, TrackedChunk> entry : this.chunks.entrySet()) {

                CompoundNBT chunkTag = new CompoundNBT();

                chunkTag.putLong("chunk", entry.getKey().toLong());

                TrackedChunk trackedChunk = entry.getValue();

                chunkTag.putBoolean("isClaimedByTracker", trackedChunk.isClaimedByTracker);

                chunkTag.putBoolean("isForceLoadedByTracker", trackedChunk.isForceLoadedByTracker);

                chunkTag.put(
                        "loaders",
                        new LongArrayNBT(
                                trackedChunk
                                        .loaders
                                        .stream()
                                        .map(BlockPos::asLong)
                                        .collect(Collectors.toList())));

                compound.put(entry.getKey().x + ";" + entry.getKey().z, chunkTag);
            }

            return compound;
        }

        public void read(CompoundNBT compound) {

            for (String key : compound.getAllKeys()) {

                CompoundNBT chunkTag = compound.getCompound(key);

                ChunkPos chunk = new ChunkPos(chunkTag.getLong("chunk"));

                if (!this.chunks.containsKey(chunk)) {
                    this.chunks.put(chunk, new TrackedChunk());
                }
                TrackedChunk trackedChunk = this.chunks.get(chunk);

                trackedChunk.isClaimedByTracker = chunkTag.getBoolean("isClaimedByTracker");

                trackedChunk.isForceLoadedByTracker = chunkTag.getBoolean("isForceLoadedByTracker");

                Arrays.stream(((LongArrayNBT) chunkTag.get("loaders")).getAsLongArray())
                        .mapToObj(BlockPos::of)
                        .forEach(trackedChunk.loaders::add);
            }
        }

        private static void logError(String message) {
            LogManager.getLogger("ChunkLoaders").log(
                    Level.ERROR,
                    message + "\n" +
                            Arrays.stream(Thread.currentThread().getStackTrace())
                                    .map(stackTraceElement -> stackTraceElement.toString())
                                    .collect(Collectors.joining("\n")));
        }

        private class TrackedChunk {
            public boolean isClaimedByTracker = false;
            public boolean isForceLoadedByTracker = false;
            public List<BlockPos> loaders = new ArrayList<>();
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.world instanceof ServerWorld))
            return;

        ServerWorld world = (ServerWorld) e.world;
        ServerChunkProvider chunkProvider = world.getChunkSource();
        int tickSpeed = world.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (tickSpeed > 0) {
            world.getCapability(TRACKER_CAPABILITY).ifPresent(tracker -> {
                for (ChunkPos pos : tracker.chunks.keySet()) {
                    if (chunkProvider.chunkMap.getPlayers(pos, false).count() == 0)
                        world.tickChunk(world.getChunk(pos.x, pos.z), tickSpeed);
                }
            });
        }
    }
}
