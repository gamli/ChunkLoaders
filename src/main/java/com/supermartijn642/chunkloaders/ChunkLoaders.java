package com.supermartijn642.chunkloaders;

import com.simibubi.create.AllMovementBehaviours;
import com.supermartijn642.chunkloaders.packet.PacketToggleChunk;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.ObjectHolder;

/**
 * Created 7/7/2020 by SuperMartijn642
 */
@Mod("chunkloaders")
public class ChunkLoaders {

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("chunkloaders", "main"), () -> "1", "1"::equals, "1"::equals);

    @ObjectHolder("chunkloaders:single_chunk_loader")
    public static Block single_chunk_loader;
    @ObjectHolder("chunkloaders:basic_chunk_loader")
    public static Block basic_chunk_loader;
    @ObjectHolder("chunkloaders:advanced_chunk_loader")
    public static Block advanced_chunk_loader;
    @ObjectHolder("chunkloaders:ultimate_chunk_loader")
    public static Block ultimate_chunk_loader;

    @ObjectHolder("chunkloaders:single_chunk_loader_tile")
    public static TileEntityType<ChunkLoaderTile> single_chunk_loader_tile;
    @ObjectHolder("chunkloaders:basic_chunk_loader_tile")
    public static TileEntityType<ChunkLoaderTile> basic_chunk_loader_tile;
    @ObjectHolder("chunkloaders:advanced_chunk_loader_tile")
    public static TileEntityType<ChunkLoaderTile> advanced_chunk_loader_tile;
    @ObjectHolder("chunkloaders:ultimate_chunk_loader_tile")
    public static TileEntityType<ChunkLoaderTile> ultimate_chunk_loader_tile;

    public ChunkLoaders(){
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::init);

        CHANNEL.registerMessage(0, PacketToggleChunk.class, PacketToggleChunk::encode, PacketToggleChunk::decode, PacketToggleChunk::handle);
    }

    public void init(FMLCommonSetupEvent e){
        ChunkLoaderUtil.register();
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlockRegistry(final RegistryEvent.Register<Block> e){
            
            ChunkLoaderMovementBehaviour chunkLoaderMovementBehaviour = new ChunkLoaderMovementBehaviour();
            
            ChunkLoaderBlock singleChunkLoader = new ChunkLoaderBlock("single_chunk_loader", ChunkLoaderBlock.SINGLE_SHAPE, () -> new ChunkLoaderTile(single_chunk_loader_tile, ChunkLoadersConfig.singleChunkLoaderRadius.get() * 2 - 1), ChunkLoadersConfig.singleChunkLoaderRadius.get() * 2 - 1);
            e.getRegistry().register(singleChunkLoader);
            AllMovementBehaviours.addMovementBehaviour(singleChunkLoader, chunkLoaderMovementBehaviour);

            ChunkLoaderBlock basicChunkLoader = new ChunkLoaderBlock("basic_chunk_loader", ChunkLoaderBlock.BASIC_SHAPE, () -> new ChunkLoaderTile(basic_chunk_loader_tile, ChunkLoadersConfig.basicChunkLoaderRadius.get() * 2 - 1), ChunkLoadersConfig.basicChunkLoaderRadius.get() * 2 - 1);
            e.getRegistry().register(basicChunkLoader);
            AllMovementBehaviours.addMovementBehaviour(basicChunkLoader, chunkLoaderMovementBehaviour);

            ChunkLoaderBlock advancedChunkLoader = new ChunkLoaderBlock("advanced_chunk_loader", ChunkLoaderBlock.ADVANCED_SHAPE, () -> new ChunkLoaderTile(advanced_chunk_loader_tile, ChunkLoadersConfig.advancedChunkLoaderRadius.get() * 2 - 1), ChunkLoadersConfig.advancedChunkLoaderRadius.get() * 2 - 1);
            e.getRegistry().register(advancedChunkLoader);
            AllMovementBehaviours.addMovementBehaviour(advancedChunkLoader, chunkLoaderMovementBehaviour);

            ChunkLoaderBlock ultimateChunkLoader = new ChunkLoaderBlock("ultimate_chunk_loader", ChunkLoaderBlock.ULTIMATE_SHAPE, () -> new ChunkLoaderTile(ultimate_chunk_loader_tile, ChunkLoadersConfig.ultimateChunkLoaderRadius.get() * 2 - 1), ChunkLoadersConfig.ultimateChunkLoaderRadius.get() * 2 - 1);
            e.getRegistry().register(ultimateChunkLoader);
            AllMovementBehaviours.addMovementBehaviour(ultimateChunkLoader, chunkLoaderMovementBehaviour);
        }

        @SubscribeEvent
        public static void onTileRegistry(final RegistryEvent.Register<TileEntityType<?>> e){
            e.getRegistry().register(TileEntityType.Builder.of(() -> new ChunkLoaderTile(single_chunk_loader_tile, 1), single_chunk_loader).build(null).setRegistryName("single_chunk_loader_tile"));
            e.getRegistry().register(TileEntityType.Builder.of(() -> new ChunkLoaderTile(basic_chunk_loader_tile, 3), basic_chunk_loader).build(null).setRegistryName("basic_chunk_loader_tile"));
            e.getRegistry().register(TileEntityType.Builder.of(() -> new ChunkLoaderTile(advanced_chunk_loader_tile, 5), advanced_chunk_loader).build(null).setRegistryName("advanced_chunk_loader_tile"));
            e.getRegistry().register(TileEntityType.Builder.of(() -> new ChunkLoaderTile(ultimate_chunk_loader_tile, 7), ultimate_chunk_loader).build(null).setRegistryName("ultimate_chunk_loader_tile"));
        }

        @SubscribeEvent
        public static void onItemRegistry(final RegistryEvent.Register<Item> e){
            e.getRegistry().register(new BlockItem(single_chunk_loader, new Item.Properties().tab(ItemGroup.TAB_SEARCH)).setRegistryName("single_chunk_loader"));
            e.getRegistry().register(new BlockItem(basic_chunk_loader, new Item.Properties().tab(ItemGroup.TAB_SEARCH)).setRegistryName("basic_chunk_loader"));
            e.getRegistry().register(new BlockItem(advanced_chunk_loader, new Item.Properties().tab(ItemGroup.TAB_SEARCH)).setRegistryName("advanced_chunk_loader"));
            e.getRegistry().register(new BlockItem(ultimate_chunk_loader, new Item.Properties().tab(ItemGroup.TAB_SEARCH)).setRegistryName("ultimate_chunk_loader"));
        }
    }

}
