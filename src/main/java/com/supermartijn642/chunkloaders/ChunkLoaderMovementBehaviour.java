package com.supermartijn642.chunkloaders;

import com.simibubi.create.content.contraptions.components.structureMovement.MovementBehaviour;
import com.simibubi.create.content.contraptions.components.structureMovement.MovementContext;
import net.minecraft.util.math.BlockPos;

public class ChunkLoaderMovementBehaviour extends MovementBehaviour {

    @Override
    public void startMoving(MovementContext context) {
        super.startMoving(context);
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        super.visitNewPosition(context, pos);
        
        if(context.world.isClientSide()) {
            return;
        }

        ChunkLoaderUtil.debug("movement.visitNewPosition() called - pos: " + pos + ", context.position: " + context.position);

        BlockPos lastLoaderPosition = (BlockPos) context.temporaryData;
        if (lastLoaderPosition != null) {
            ChunkLoaderTile.unloadChunks(context.world, lastLoaderPosition, context.tileData.getCompound("data"));
        }
        ChunkLoaderTile.loadChunks(context.world, pos, context.tileData.getCompound("data"));

        context.temporaryData = pos;
    }

    @Override
    public void stopMoving(MovementContext context) {
        super.stopMoving(context);

        if(context.world.isClientSide()) {
            return;
        }

        ChunkLoaderUtil.debug("movement.stopMoving() called");
        
        BlockPos lastLoaderPosition = (BlockPos) context.temporaryData;
        context.temporaryData = null;
        if (lastLoaderPosition != null) {
            ChunkLoaderTile.unloadChunks(context.world, lastLoaderPosition, context.tileData.getCompound("data"));
        }
    }

    @Override
    public boolean renderAsNormalTileEntity() {
        return true;
    }
}
