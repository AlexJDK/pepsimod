/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2017 Team Pepsi
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from Team Pepsi.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: Team Pepsi), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.daporkchop.pepsimod.wdl;

import net.daporkchop.pepsimod.wdl.api.IWorldLoadListener;
import net.daporkchop.pepsimod.wdl.api.WDLApi;
import net.daporkchop.pepsimod.wdl.api.WDLApi.ModInfo;
import net.daporkchop.pepsimod.wdl.update.WDLUpdateChecker;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.*;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapData;

import java.lang.reflect.Field;

/**
 * Handles all of the events for WDL.
 * <p>
 * These should be called regardless of whether downloading is
 * active; they handle that logic themselves.
 * <br/>
 * The difference between this class and {@link WDLHooks} is that WDLEvents
 * should be called directly from the source and does a bit of processing, while
 */
public class WDLEvents {
    private static final Profiler PROFILER = Minecraft.getMinecraft().mcProfiler;

    /**
     * Must be called after the static World object in Minecraft has been
     * replaced.
     */
    public static void onWorldLoad(WorldClient world) {
        PROFILER.startSection("Core");

        if (WDL.minecraft.isIntegratedServerRunning()) {
            // Don't do anything else in single player

            PROFILER.endSection();  // "Core"
            return;
        }

        // If already downloading
        if (WDL.downloading) {
            // If not currently saving, stop the current download and start
            // saving now
            if (!WDL.saving) {
                WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
                        "net.daporkchop.pepsimod.wdl.messages.generalInfo.worldChanged");
                WDL.worldLoadingDeferred = true;
                WDL.startSaveThread();
            }

            PROFILER.endSection();  // "Core"
            return;
        }

        boolean sameServer = WDL.loadWorld();

        WDLUpdateChecker.startIfNeeded();  // TODO: Always check for updates, even in single player

        PROFILER.endSection();  // "Core"

        for (ModInfo<IWorldLoadListener> info : WDLApi
                .getImplementingExtensions(IWorldLoadListener.class)) {
            PROFILER.startSection(info.id);
            info.mod.onWorldLoad(world, sameServer);
            PROFILER.endSection();  // info.id
        }
    }

    /**
     * Must be called when a chunk is no longer needed and is about to be removed.
     */
    public static void onChunkNoLongerNeeded(Chunk unneededChunk) {
        if (!WDL.downloading) {
            return;
        }

        if (unneededChunk == null) {
            return;
        }

        if (WDLPluginChannels.canSaveChunk(unneededChunk)) {
            WDLMessages.chatMessageTranslated(
                    WDLMessageTypes.ON_CHUNK_NO_LONGER_NEEDED,
                    "net.daporkchop.pepsimod.wdl.messages.onChunkNoLongerNeeded.saved",
                    unneededChunk.x, unneededChunk.z);
            WDL.saveChunk(unneededChunk);
        } else {
            WDLMessages.chatMessageTranslated(
                    WDLMessageTypes.ON_CHUNK_NO_LONGER_NEEDED,
                    "net.daporkchop.pepsimod.wdl.messages.onChunkNoLongerNeeded.didNotSave",
                    unneededChunk.x, unneededChunk.z);
        }
    }

    /**
     * Must be called when a GUI that receives item stacks from the server is
     * shown.
     */
    public static void onItemGuiOpened() {
        if (!WDL.downloading) {
            return;
        }

        if (WDL.minecraft.objectMouseOver == null) {
            return;
        }

        if (WDL.minecraft.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY) {
            WDL.lastEntity = WDL.minecraft.objectMouseOver.entityHit;
        } else {
            WDL.lastEntity = null;
            WDL.lastClickedBlock = WDL.minecraft.objectMouseOver.getBlockPos();
        }
    }

    /**
     * Must be called when a GUI that triggered an onItemGuiOpened is no longer
     * shown.
     */
    public static boolean onItemGuiClosed() {
        if (!WDL.downloading) {
            return true;
        }

        if (WDL.windowContainer == null ||
                ReflectionUtils.isCreativeContainer(WDL.windowContainer.getClass())) {
            // Can't do anything with null containers or the creative inventory
            return true;
        }

        String saveName = "";

        if (WDL.thePlayer.getRidingEntity() instanceof AbstractHorse) {
            //If the player is on a horse, check if they are opening the
            //inventory of the horse they are on.  If so, use that,
            //rather than the entity being looked at.
            if (WDL.windowContainer instanceof ContainerHorseInventory) {
                AbstractHorse horseInContainer = ReflectionUtils
                        .findAndGetPrivateField(WDL.windowContainer,
                                AbstractHorse.class);

                //Intentional reference equals
                if (horseInContainer == WDL.thePlayer.getRidingEntity()) {
                    if (!WDLPluginChannels.canSaveEntities(
                            horseInContainer.chunkCoordX,
                            horseInContainer.chunkCoordZ)) {
                        //I'm not 100% sure the chunkCoord stuff will have been
                        //set up at this point.  Might cause bugs.
                        WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_INFO,
                                "net.daporkchop.pepsimod.wdl.messages.onGuiClosedInfo.cannotSaveEntities");
                        return true;
                    }

                    AbstractHorse entityHorse = (AbstractHorse)
                            WDL.thePlayer.getRidingEntity();
                    saveHorse((ContainerHorseInventory) WDL.windowContainer, entityHorse);

                    WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_INFO,
                            "net.daporkchop.pepsimod.wdl.messages.onGuiClosedInfo.savedRiddenHorse");
                    return true;
                }
            }
        }

        // If the last thing clicked was an ENTITY
        if (WDL.lastEntity != null) {
            if (!WDLPluginChannels.canSaveEntities(WDL.lastEntity.chunkCoordX,
                    WDL.lastEntity.chunkCoordZ)) {
                WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_INFO,
                        "net.daporkchop.pepsimod.wdl.messages.onGuiClosedInfo.cannotSaveEntities");
                return true;
            }

            if (WDL.lastEntity instanceof EntityMinecartChest
                    && WDL.windowContainer instanceof ContainerChest) {
                EntityMinecartChest emcc = (EntityMinecartChest) WDL.lastEntity;

                for (int i = 0; i < emcc.getSizeInventory(); i++) {
                    Slot slot = WDL.windowContainer.getSlot(i);
                    if (slot.getHasStack()) {
                        emcc.setInventorySlotContents(i, slot.getStack());
                    }
                }

                saveName = "storageMinecart";
            } else if (WDL.lastEntity instanceof EntityMinecartHopper
                    && WDL.windowContainer instanceof ContainerHopper) {
                EntityMinecartHopper emch = (EntityMinecartHopper) WDL.lastEntity;

                for (int i = 0; i < emch.getSizeInventory(); i++) {
                    Slot slot = WDL.windowContainer.getSlot(i);
                    if (slot.getHasStack()) {
                        emch.setInventorySlotContents(i, slot.getStack());
                    }
                }

                saveName = "hopperMinecart";
            } else if (WDL.lastEntity instanceof EntityVillager
                    && WDL.windowContainer instanceof ContainerMerchant) {
                EntityVillager ev = (EntityVillager) WDL.lastEntity;

                IMerchant merchant = ReflectionUtils.findAndGetPrivateField(
                        WDL.windowContainer, IMerchant.class);
                MerchantRecipeList list = merchant.getRecipes(WDL.thePlayer);
                ReflectionUtils.findAndSetPrivateField(ev, MerchantRecipeList.class, list);

                try {
                    ITextComponent displayName = merchant.getDisplayName();
                    if (!(displayName instanceof TextComponentTranslation)) {
                        // Taking the toString to reflect JSON structure
                        String componentDesc = String.valueOf(displayName);
                        throw new CommandException("net.daporkchop.pepsimod.wdl.messages.onGuiClosedWarning.villagerCareer.notAComponent", componentDesc);
                    }

                    TextComponentTranslation displayNameTranslation = ((TextComponentTranslation) displayName);
                    String key = displayNameTranslation.getKey();

                    int career = EntityUtils.getCareer(key, ev.getProfession());

                    // XXX Iteration order of fields is undefined, and this is generally sloppy
                    // careerId is the 4th field
                    int fieldIndex = 0;
                    Field careerIdField = null;
                    for (Field field : EntityVillager.class.getDeclaredFields()) {
                        if (field.getType().equals(int.class)) {
                            fieldIndex++;
                            if (fieldIndex == 4) {
                                careerIdField = field;
                                break;
                            }
                        }
                    }
                    if (careerIdField == null) {
                        throw new CommandException("net.daporkchop.pepsimod.wdl.messages.onGuiClosedWarning.villagerCareer.professionField");
                    }

                    careerIdField.setAccessible(true);
                    careerIdField.setInt(ev, career);

                    // Re-create this component rather than modifying the old one
                    ITextComponent dispCareer = new TextComponentTranslation(key, displayNameTranslation.getFormatArgs());
                    dispCareer.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(key)));

                    WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_INFO, "net.daporkchop.pepsimod.wdl.messages.onGuiClosedInfo.savedEntity.villager.career", dispCareer, career);
                } catch (CommandException ex) {
                    WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_WARNING, ex.getMessage(), ex.getErrorObjects());
                } catch (Throwable ex) {
                    WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_WARNING, "net.daporkchop.pepsimod.wdl.messages.onGuiClosedWarning.villagerCareer.exception", ex);
                }

                saveName = "villager";
            } else if (WDL.lastEntity instanceof AbstractHorse
                    && WDL.windowContainer instanceof ContainerHorseInventory) {
                saveHorse((ContainerHorseInventory) WDL.windowContainer,
                        (AbstractHorse) WDL.lastEntity);

                saveName = "horse";
            } else {
                return false;
            }

            WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_INFO,
                    "net.daporkchop.pepsimod.wdl.messages.onGuiClosedInfo.savedEntity." + saveName);
            return true;
        }

        // Else, the last thing clicked was a TILE ENTITY

        // Get the tile entity which we are going to update the inventory for
        TileEntity te = WDL.worldClient.getTileEntity(WDL.lastClickedBlock);

        if (te == null) {
            //TODO: Is this a good way to stop?  Is the event truely handled here?
            WDLMessages.chatMessageTranslated(
                    WDLMessageTypes.ON_GUI_CLOSED_WARNING,
                    "net.daporkchop.pepsimod.wdl.messages.onGuiClosedWarning.couldNotGetTE",
                    WDL.lastClickedBlock);
            return true;
        }

        //Permissions check.
        if (!WDLPluginChannels.canSaveContainers(te.getPos().getX() << 4, te
                .getPos().getZ() << 4)) {
            WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_INFO,
                    "net.daporkchop.pepsimod.wdl.messages.onGuiClosedInfo.cannotSaveTileEntities");
            return true;
        }

        if (WDL.windowContainer instanceof ContainerChest
                && te instanceof TileEntityChest) {
            if (WDL.windowContainer.inventorySlots.size() > 63) {
                // This is messy, but it needs to be like this because
                // the left and right chests must be in the right positions.

                BlockPos pos1, pos2;
                TileEntity te1, te2;

                pos1 = WDL.lastClickedBlock;
                te1 = te;

                // We need seperate variables for the above reason --
                // pos1 isn't always the same as chestPos1 (and thus
                // chest1 isn't always te1).
                BlockPos chestPos1 = null, chestPos2 = null;
                TileEntityChest chest1 = null, chest2 = null;

                pos2 = pos1.add(0, 0, 1);
                te2 = WDL.worldClient.getTileEntity(pos2);
                if (te2 instanceof TileEntityChest &&
                        ((TileEntityChest) te2).getChestType() ==
                                ((TileEntityChest) te1).getChestType()) {

                    chest1 = (TileEntityChest) te1;
                    chest2 = (TileEntityChest) te2;

                    chestPos1 = pos1;
                    chestPos2 = pos2;
                }

                pos2 = pos1.add(0, 0, -1);
                te2 = WDL.worldClient.getTileEntity(pos2);
                if (te2 instanceof TileEntityChest &&
                        ((TileEntityChest) te2).getChestType() ==
                                ((TileEntityChest) te1).getChestType()) {

                    chest1 = (TileEntityChest) te2;
                    chest2 = (TileEntityChest) te1;

                    chestPos1 = pos2;
                    chestPos2 = pos1;
                }

                pos2 = pos1.add(1, 0, 0);
                te2 = WDL.worldClient.getTileEntity(pos2);
                if (te2 instanceof TileEntityChest &&
                        ((TileEntityChest) te2).getChestType() ==
                                ((TileEntityChest) te1).getChestType()) {
                    chest1 = (TileEntityChest) te1;
                    chest2 = (TileEntityChest) te2;

                    chestPos1 = pos1;
                    chestPos2 = pos2;
                }

                pos2 = pos1.add(-1, 0, 0);
                te2 = WDL.worldClient.getTileEntity(pos2);
                if (te2 instanceof TileEntityChest &&
                        ((TileEntityChest) te2).getChestType() ==
                                ((TileEntityChest) te1).getChestType()) {
                    chest1 = (TileEntityChest) te2;
                    chest2 = (TileEntityChest) te1;

                    chestPos1 = pos2;
                    chestPos2 = pos1;
                }

                if (chest1 == null || chest2 == null ||
                        chestPos1 == null || chestPos2 == null) {
                    WDLMessages.chatMessageTranslated(WDLMessageTypes.ERROR,
                            "net.daporkchop.pepsimod.wdl.messages.onGuiClosedWarning.failedToFindDoubleChest");
                    return true;
                }

                WDL.saveContainerItems(WDL.windowContainer, chest1, 0);
                WDL.saveContainerItems(WDL.windowContainer, chest2, 27);
                WDL.saveTileEntity(chestPos1, chest1);
                WDL.saveTileEntity(chestPos2, chest2);

                saveName = "doubleChest";
            }
            // basic chest
            else {
                WDL.saveContainerItems(WDL.windowContainer, (TileEntityChest) te, 0);
                WDL.saveTileEntity(WDL.lastClickedBlock, te);
                saveName = "singleChest";
            }
        } else if (WDL.windowContainer instanceof ContainerChest
                && te instanceof TileEntityEnderChest) {
            InventoryEnderChest inventoryEnderChest = WDL.thePlayer
                    .getInventoryEnderChest();
            int inventorySize = inventoryEnderChest.getSizeInventory();
            int containerSize = WDL.windowContainer.inventorySlots.size();

            for (int i = 0; i < containerSize && i < inventorySize; i++) {
                Slot slot = WDL.windowContainer.getSlot(i);
                if (slot.getHasStack()) {
                    inventoryEnderChest.setInventorySlotContents(i, slot.getStack());
                }
            }

            saveName = "enderChest";
        } else if (WDL.windowContainer instanceof ContainerBrewingStand
                && te instanceof TileEntityBrewingStand) {
            IInventory brewingInventory = ReflectionUtils.findAndGetPrivateField(
                    WDL.windowContainer, IInventory.class);
            WDL.saveContainerItems(WDL.windowContainer, (TileEntityBrewingStand) te, 0);
            WDL.saveInventoryFields(brewingInventory, (TileEntityBrewingStand) te);
            WDL.saveTileEntity(WDL.lastClickedBlock, te);
            saveName = "brewingStand";
        } else if (WDL.windowContainer instanceof ContainerDispenser
                && te instanceof TileEntityDispenser) {
            WDL.saveContainerItems(WDL.windowContainer, (TileEntityDispenser) te, 0);
            WDL.saveTileEntity(WDL.lastClickedBlock, te);
            saveName = "dispenser";
        } else if (WDL.windowContainer instanceof ContainerFurnace
                && te instanceof TileEntityFurnace) {
            IInventory furnaceInventory = ReflectionUtils.findAndGetPrivateField(
                    WDL.windowContainer, IInventory.class);
            WDL.saveContainerItems(WDL.windowContainer, (TileEntityFurnace) te, 0);
            WDL.saveInventoryFields(furnaceInventory, (TileEntityFurnace) te);
            WDL.saveTileEntity(WDL.lastClickedBlock, te);
            saveName = "furnace";
        } else if (WDL.windowContainer instanceof ContainerHopper
                && te instanceof TileEntityHopper) {
            WDL.saveContainerItems(WDL.windowContainer, (TileEntityHopper) te, 0);
            WDL.saveTileEntity(WDL.lastClickedBlock, te);
            saveName = "hopper";
        } else if (WDL.windowContainer instanceof ContainerBeacon
                && te instanceof TileEntityBeacon) {
            IInventory beaconInventory =
                    ((ContainerBeacon) WDL.windowContainer).getTileEntity();
            TileEntityBeacon savedBeacon = (TileEntityBeacon) te;
            WDL.saveContainerItems(WDL.windowContainer, savedBeacon, 0);
            WDL.saveInventoryFields(beaconInventory, savedBeacon);
            WDL.saveTileEntity(WDL.lastClickedBlock, te);
            saveName = "beacon";
        } else {
            return false;
        }

        WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_GUI_CLOSED_INFO,
                "net.daporkchop.pepsimod.wdl.messages.onGuiClosedInfo.savedTileEntity." + saveName);
        return true;
    }

    /**
     * Must be called when a block event is scheduled for the next tick. The
     * caller has to check if WDL.downloading is true!
     */
    public static void onBlockEvent(BlockPos pos, Block block, int event,
                                    int param) {
        if (!WDL.downloading) {
            return;
        }

        if (!WDLPluginChannels.canSaveTileEntities(pos.getX() << 4,
                pos.getZ() << 4)) {
            return;
        }
        if (block == Blocks.NOTEBLOCK) {
            TileEntityNote newTE = new TileEntityNote();
            newTE.note = (byte) (param % 25);
            WDL.worldClient.setTileEntity(pos, newTE);
            WDL.saveTileEntity(pos, newTE);
            WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_BLOCK_EVENT,
                    "net.daporkchop.pepsimod.wdl.messages.onBlockEvent.noteblock", pos, param, newTE);
        }
    }

    /**
     * Must be called when a Map Data packet is received, to store the image on
     * the map item.
     */
    public static void onMapDataLoaded(int mapID,
                                       MapData mapData) {
        if (!WDL.downloading) {
            return;
        }

        if (!WDLPluginChannels.canSaveMaps()) {
            return;
        }

        WDL.newMapDatas.put(mapID, mapData);

        WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_MAP_SAVED,
                "net.daporkchop.pepsimod.wdl.messages.onMapSaved", mapID);
    }

    /**
     * Must be called whenever a plugin channel message / custom payload packet
     * is received.
     */
    public static void onPluginChannelPacket(String channel,
                                             byte[] bytes) {
        WDLPluginChannels.onPluginChannelPacket(channel, bytes);
    }

    /**
     * Must be called when an entity is about to be removed from the world.
     */
    public static void onRemoveEntityFromWorld(Entity entity) {
        // If the entity is being removed and it's outside the default tracking
        // range, go ahead and remember it until the chunk is saved.
        if (WDL.downloading && entity != null
                && WDLPluginChannels.canSaveEntities(entity.chunkCoordX,
                entity.chunkCoordZ)) {
            if (!EntityUtils.isEntityEnabled(entity)) {
                WDLMessages.chatMessageTranslated(
                        WDLMessageTypes.REMOVE_ENTITY,
                        "net.daporkchop.pepsimod.wdl.messages.removeEntity.allowingRemoveUserPref",
                        entity);
                return;
            }

            int threshold = EntityUtils.getEntityTrackDistance(entity);

            if (threshold < 0) {
                WDLMessages.chatMessageTranslated(
                        WDLMessageTypes.REMOVE_ENTITY,
                        "net.daporkchop.pepsimod.wdl.messages.removeEntity.allowingRemoveUnrecognizedDistance",
                        entity);
                return;
            }

            double distance = entity.getDistance(WDL.thePlayer.posX,
                    entity.posY, WDL.thePlayer.posZ);

            if (distance > threshold) {
                WDLMessages.chatMessageTranslated(
                        WDLMessageTypes.REMOVE_ENTITY,
                        "net.daporkchop.pepsimod.wdl.messages.removeEntity.savingDistance",
                        entity, distance, threshold);
                entity.chunkCoordX = MathHelper
                        .floor(entity.posX / 16.0D);
                entity.chunkCoordZ = MathHelper
                        .floor(entity.posZ / 16.0D);

                WDL.newEntities.put(new ChunkPos(entity.chunkCoordX,
                        entity.chunkCoordZ), entity);
                return;
            }

            WDLMessages.chatMessageTranslated(
                    WDLMessageTypes.REMOVE_ENTITY,
                    "net.daporkchop.pepsimod.wdl.messages.removeEntity.allowingRemoveDistance",
                    entity, distance, threshold);
        }
    }

    /**
     * Called upon any chat message.  Used for getting the seed.
     */
    public static void onChatMessage(String msg) {
        if (WDL.downloading && msg.startsWith("Seed: ")) {
            String seed = msg.substring(6);
            WDL.worldProps.setProperty("RandomSeed", seed);

            if (WDL.worldProps.getProperty("MapGenerator", "void").equals("void")) {

                WDL.worldProps.setProperty("MapGenerator", "default");
                WDL.worldProps.setProperty("GeneratorName", "default");
                WDL.worldProps.setProperty("GeneratorVersion", "1");
                WDL.worldProps.setProperty("GeneratorOptions", "");

                WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
                        "net.daporkchop.pepsimod.wdl.messages.generalInfo.seedAndGenSet", seed);
            } else {
                WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
                        "net.daporkchop.pepsimod.wdl.messages.generalInfo.seedSet", seed);
            }
        }
    }

    /**
     * Saves all data for a horse into its inventory.
     *
     * @param container
     * @param horse
     */
    private static void saveHorse(ContainerHorseInventory container, AbstractHorse horse) {
        final int PLAYER_INVENTORY_SLOTS = 4 * 9;
        ContainerHorseChest horseInventory = new ContainerHorseChest(
                "HorseChest", container.inventorySlots.size()
                - PLAYER_INVENTORY_SLOTS);
        for (int i = 0; i < horseInventory.getSizeInventory(); i++) {
            Slot slot = container.getSlot(i);
            if (slot.getHasStack()) {
                horseInventory.setInventorySlotContents(i, slot.getStack());
            }
        }

        ReflectionUtils.findAndSetPrivateField(horse, AbstractHorse.class, ContainerHorseChest.class, horseInventory);
    }
}
