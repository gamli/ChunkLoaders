package com.supermartijn642.chunkloaders;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * Created 7/10/2020 by SuperMartijn642
 */
public class ChunkLoaderTile extends TileEntity {

    public static final String NBT_TEAM_ID = "teamId";
    public static final String NBT_PLAYER_ID = "playerId";
    public static final String NBT_GRID_SIZE = "gridSize";

    public final int animationOffset = new Random().nextInt(20000);

    private UUID teamId;
    private UUID playerId;
    private int gridSize;
    private int radius;
    private boolean[][] grid; // [x][z]

    private boolean dataChanged = false;

    public ChunkLoaderTile(TileEntityType<?> tileEntityTypeIn, int gridSize) {
        super(tileEntityTypeIn);
        this.gridSize = gridSize;
        this.radius = (gridSize - 1) / 2;
        this.grid = new boolean[gridSize][gridSize];
    }

    public void setPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        
        if (isClientSide(worldIn)) {
            return;
        }

        ChunkLoaderUtil.debug("tile.setPlacedBy() called");

        PlayerEntity player;
        if (placer instanceof PlayerEntity) {
            player = (PlayerEntity) placer;
        } else {
            ChunkLoaderUtil.error("Not placed by player - placer: " + placer);
            return;
        }

        UUID playerId = player.getUUID();
        this.playerId = playerId;
        this.teamId = FTBTeamsAPI.getPlayerTeam(playerId).getId();
        for (int x = 0; x < this.gridSize; x++) {
            for (int z = 0; z < this.gridSize; z++) {
                this.grid[x][z] = true;
            }
        }

        this.dataChanged();
    }

    @Override
    public void setRemoved() {

        super.setRemoved();

        if (isClientSide()) {
            return;
        }

        ChunkLoaderUtil.debug("tile.setRemoved() called");

        if (this.teamId != null && this.playerId != null) {
            this.level.getCapability(ChunkLoaderUtil.TRACKER_CAPABILITY).ifPresent(tracker -> {
                ChunkPos pos = this.level.getChunk(this.worldPosition).getPos();
                for (int x = 0; x < this.gridSize; x++) {
                    for (int z = 0; z < this.gridSize; z++) {
                        if (this.grid[x][z]) {
                            tracker.remove(
                                    new ChunkPos(pos.x + x - radius, pos.z + z - radius),
                                    this.worldPosition,
                                    this.teamId,
                                    this.playerId);
                        }
                    }
                }
            });
        }
    }

    public void onPlace() {

        if (isClientSide()) {
            return;
        }
        
        ChunkLoaderUtil.debug("tile.onPlace() called");
        if (this.teamId != null && this.playerId != null) {
            loadChunks();
        } else {
            ChunkLoaderUtil.debug("Missing team or player - team: " + this.teamId + ", player: " + this.playerId);
        }
    }

    @Override
    public void onLoad() {

        if (isClientSide()) {
            return;
        }
        
        ChunkLoaderUtil.debug("tile.onLoad() called");
        super.onLoad();
        if (this.teamId != null && this.playerId != null) {
            loadChunks();
        } else {
            ChunkLoaderUtil.debug("Missing team or player - team: " + this.teamId + ", player: " + this.playerId);
        }
    }

    private void loadChunks() {
        ChunkLoaderUtil.debug("tile.loadChunks() called");
        this.level.getCapability(ChunkLoaderUtil.TRACKER_CAPABILITY).ifPresent(tracker -> {
            ChunkPos chunkPos = this.level.getChunk(this.worldPosition).getPos();
            for (int x = 0; x < this.gridSize; x++) {
                for (int z = 0; z < this.gridSize; z++) {
                    if (this.grid[x][z]) {
                        tracker.add(
                                new ChunkPos(chunkPos.x + x - radius, chunkPos.z + z - radius),
                                this.worldPosition,
                                this.teamId,
                                this.playerId);
                    }
                }
            }
        });
    }

    public static void loadChunks(World world, BlockPos loaderPosition, CompoundNBT tag) {

        ChunkLoaderUtil.debug("tile.static.loadChunks() called");

        UUID team = readTeam(tag);

        UUID player = readPlayer(tag);

        ImmutableTriple<Integer, Integer, boolean[][]> sizeRadiusGrid = readSizeRadiusGrid(tag);
        Integer gridSize = sizeRadiusGrid.left;
        if (gridSize == null) {
            ChunkLoaderUtil.error("Missing gridSize");
        }
        int radius = sizeRadiusGrid.middle;
        boolean[][] grid = sizeRadiusGrid.right;

        if (team != null && player != null) {
            world.getCapability(ChunkLoaderUtil.TRACKER_CAPABILITY).ifPresent(tracker -> {
                ChunkPos chunkPos = world.getChunk(loaderPosition).getPos();
                for (int x = 0; x < gridSize; x++) {
                    for (int z = 0; z < gridSize; z++) {
                        if (grid[x][z]) {
                            tracker.add(
                                    new ChunkPos(chunkPos.x + x - radius, chunkPos.z + z - radius),
                                    loaderPosition,
                                    team,
                                    player);
                        }
                    }
                }
            });
        } else {
            ChunkLoaderUtil.error("Missing team or player - team: " + team + ", player: " + player);
        }
    }

    public static void unloadChunks(World world, BlockPos loaderPosition, CompoundNBT tag) {

        ChunkLoaderUtil.debug("tile.static.unloadChunks() called");

        UUID team = readTeam(tag);

        UUID player = readPlayer(tag);

        ImmutableTriple<Integer, Integer, boolean[][]> sizeRadiusGrid = readSizeRadiusGrid(tag);
        Integer gridSize = sizeRadiusGrid.left;
        if (gridSize == null) {
            ChunkLoaderUtil.error("Missing gridSize");
        }
        int radius = sizeRadiusGrid.middle;
        boolean[][] grid = sizeRadiusGrid.right;

        if (team != null && player != null) {
            world.getCapability(ChunkLoaderUtil.TRACKER_CAPABILITY).ifPresent(tracker -> {
                ChunkPos pos = world.getChunk(loaderPosition).getPos();
                for (int x = 0; x < gridSize; x++) {
                    for (int z = 0; z < gridSize; z++) {
                        if (grid[x][z]) {
                            tracker.remove(
                                    new ChunkPos(pos.x + x - radius, pos.z + z - radius),
                                    loaderPosition,
                                    team,
                                    player);
                        }
                    }
                }
            });
        } else {
            ChunkLoaderUtil.error("Missing team or player - team: " + team + ", player: " + player);
        }
    }

    public void toggle(int xOffset, int zOffset) {

        if (isClientSide()) {
            return;
        }
        
        ChunkLoaderUtil.debug("tile.onToggle() called");
        this.level.getCapability(ChunkLoaderUtil.TRACKER_CAPABILITY).ifPresent(tracker -> {
            ChunkPos pos = this.level.getChunk(this.worldPosition).getPos();
            if (this.grid[xOffset + radius][zOffset + radius]) {
                tracker.remove(
                        new ChunkPos(pos.x + xOffset, pos.z + zOffset),
                        this.worldPosition,
                        this.teamId,
                        this.playerId);
            } else {
                tracker.add(
                        new ChunkPos(pos.x + xOffset, pos.z + zOffset),
                        this.worldPosition,
                        this.teamId,
                        this.playerId);
            }
            this.grid[xOffset + radius][zOffset + radius] = !this.grid[xOffset + radius][zOffset + radius];
        });
        this.dataChanged();
    }

    public boolean isLoaded(int xOffset, int zOffset) {
        return this.grid[xOffset + radius][zOffset + radius];
    }

    public int getGridSize() {
        return this.gridSize;
    }

    private boolean isClientSide() {
        return isClientSide(this.level);
    }
    
    private boolean isClientSide(World world) {
        return world == null || world.isClientSide();
    }

    public void dataChanged() {
        if (this.level.isClientSide) {
            return;
        }
        this.dataChanged = true;
        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
    }

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        compound.put("data", this.getData());
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        ChunkLoaderUtil.debug("tile.load() called - NBT: " + compound);
        super.load(state, compound);
        this.handleData(compound.getCompound("data"));
    }

    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.getUpdateTag();
        tag.put("data", this.getData());
        return tag;
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        ChunkLoaderUtil.debug("tile.handleUpdateTag() called");
        super.handleUpdateTag(state, tag);
        this.handleData(tag.getCompound("data"));
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        if (this.dataChanged) {
            this.dataChanged = false;
            return new SUpdateTileEntityPacket(this.worldPosition, 0, this.getData());
        }
        return null;
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        ChunkLoaderUtil.debug("tile.onDataPacket() called");
        this.handleData(pkt.getTag());
    }

    private CompoundNBT getData() {

        ChunkLoaderUtil.debug("tile.getData() called");

        CompoundNBT tag = new CompoundNBT();

        if(this.teamId != null) {
            tag.putUUID(NBT_TEAM_ID, this.teamId);
            ChunkLoaderUtil.debug("tile.getData() teamId: " + tag.getUUID(NBT_TEAM_ID));
        }

        if(this.playerId != null) {
            tag.putUUID(NBT_PLAYER_ID, this.playerId);
            ChunkLoaderUtil.debug("tile.getData() playerId: " + tag.getUUID(NBT_PLAYER_ID));
        }

        tag.putInt(NBT_GRID_SIZE, this.gridSize);
        for (int x = 0; x < this.gridSize; x++) {
            for (int z = 0; z < this.gridSize; z++) {
                tag.putBoolean(x + ";" + z, this.grid[x][z]);
            }
        }

        return tag;
    }

    private void handleData(CompoundNBT tag) {

        ChunkLoaderUtil.debug("tile.handleData() called");

        this.teamId = readTeam(tag);
        ChunkLoaderUtil.debug("tile.handleData() teamId: " + teamId);

        this.playerId = readPlayer(tag);
        ChunkLoaderUtil.debug("tile.handleData() playerId: " + playerId);

        ImmutableTriple<Integer, Integer, boolean[][]> sizeRadiusGrid = readSizeRadiusGrid(tag);

        if (sizeRadiusGrid.left == null) {
            ChunkLoaderUtil.error("Missing gridSize");
            return;
        }
        this.gridSize = sizeRadiusGrid.left;
        this.radius = sizeRadiusGrid.middle;
        this.grid = sizeRadiusGrid.right;

        // TODO smelly
        if(this.level != null && !this.level.isClientSide()) {
            this.dataChanged();
        }
    }

    private static UUID readTeam(CompoundNBT tag) {
        return readData(tag, NBT_TEAM_ID, dataTag -> dataTag.getUUID(NBT_TEAM_ID));
    }

    private static UUID readPlayer(CompoundNBT tag) {
        return readData(tag, NBT_PLAYER_ID, dataTag -> dataTag.getUUID(NBT_PLAYER_ID));
    }

    private static ImmutableTriple<Integer, Integer, boolean[][]> /*[x][z]*/ readSizeRadiusGrid(CompoundNBT tag) {

        Integer gridSize = readData(tag, NBT_GRID_SIZE, dataTag -> dataTag.getInt(NBT_GRID_SIZE));

        if (gridSize == null) {
            return new ImmutableTriple<>(null, null, null);
        }

        int radius = (gridSize - 1) / 2;

        boolean[][] grid = new boolean[gridSize][gridSize];
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                String xzKey = x + ";" + z;
                Boolean xzValue = readData(tag, xzKey, dataTag -> dataTag.getBoolean(xzKey));
                grid[x][z] = xzValue == null ? false : xzValue;
            }
        }
        return new ImmutableTriple<>(gridSize, radius, grid);
    }

    private static <TValue> TValue readData(CompoundNBT tag, String dataKey, Function<CompoundNBT, TValue> readValue) {
        if (!tag.contains(dataKey)) {
            return null;
        }
        return readValue.apply(tag);
    }
}
