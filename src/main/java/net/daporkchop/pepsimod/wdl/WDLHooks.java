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

import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import net.daporkchop.pepsimod.wdl.api.*;
import net.daporkchop.pepsimod.wdl.api.WDLApi.ModInfo;
import net.daporkchop.pepsimod.wdl.gui.GuiWDL;
import net.daporkchop.pepsimod.wdl.gui.GuiWDLAbout;
import net.daporkchop.pepsimod.wdl.gui.GuiWDLChunkOverrides;
import net.daporkchop.pepsimod.wdl.gui.GuiWDLPermissions;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemMap;
import net.minecraft.network.play.server.SPacketBlockAction;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketMaps;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * The various hooks for WDL. <br/>
 * All of these should be called regardless of any WDL state variables.
 */
public class WDLHooks {
    private static final Profiler PROFILER = Minecraft.getMinecraft().mcProfiler;
    private static final Logger LOGGER = LogManager.getLogger();
    /**
     * Start button ID. Ascii-encoded 'WDLs' (World Downloader Start).
     * Chosen to be unique.
     */
    private static final int WDLs = ('W' << 24) | ('D' << 16) | ('L' << 8) | ('s');
    /**
     * Options button ID. Ascii-encoded 'WDLo' (World Downloader Options).
     * Chosen to be unique.
     */
    private static final int WDLo = ('W' << 24) | ('D' << 16) | ('L' << 8) | ('o');

    /**
     * Called when {@link WorldClient#tick()} is called.
     * <br/>
     * Should be at end of the method.
     */
    public static void onWorldClientTick(WorldClient sender) {
        try {
            PROFILER.startSection("net/daporkchop/pepsimod/wdl");

            List<EntityPlayer> players = ImmutableList.copyOf(sender.playerEntities);

            if (sender != WDL.worldClient) {
                PROFILER.startSection("onWorldLoad");
                if (WDL.worldLoadingDeferred) {
                    return;
                }

                WDLEvents.onWorldLoad(sender);
                PROFILER.endSection();  // "onWorldLoad"
            } else {
                PROFILER.startSection("inventoryCheck");
                if (WDL.downloading && WDL.thePlayer != null) {
                    if (WDL.thePlayer.openContainer != WDL.windowContainer) {
                        if (WDL.thePlayer.openContainer == WDL.thePlayer.inventoryContainer) {
                            boolean handled;

                            PROFILER.startSection("onItemGuiClosed");
                            PROFILER.startSection("Core");
                            handled = WDLEvents.onItemGuiClosed();
                            PROFILER.endSection();  // "Core"

                            Container container = WDL.thePlayer.openContainer;
                            if (WDL.lastEntity != null) {
                                Entity entity = WDL.lastEntity;

                                for (ModInfo<IGuiHooksListener> info : WDLApi
                                        .getImplementingExtensions(IGuiHooksListener.class)) {
                                    if (handled) {
                                        break;
                                    }

                                    PROFILER.startSection(info.id);
                                    handled = info.mod.onEntityGuiClosed(
                                            sender, entity, container);
                                    PROFILER.endSection();  // info.id
                                }

                                if (!handled) {
                                    WDLMessages.chatMessageTranslated(
                                            WDLMessageTypes.ON_GUI_CLOSED_WARNING,
                                            "net.daporkchop.pepsimod.wdl.messages.onGuiClosedWarning.unhandledEntity",
                                            entity);
                                }
                            } else {
                                BlockPos pos = WDL.lastClickedBlock;
                                for (ModInfo<IGuiHooksListener> info : WDLApi
                                        .getImplementingExtensions(IGuiHooksListener.class)) {
                                    if (handled) {
                                        break;
                                    }

                                    PROFILER.startSection(info.id);
                                    handled = info.mod.onBlockGuiClosed(
                                            sender, pos, container);
                                    PROFILER.endSection();  // info.id
                                }

                                if (!handled) {
                                    WDLMessages.chatMessageTranslated(
                                            WDLMessageTypes.ON_GUI_CLOSED_WARNING,
                                            "net.daporkchop.pepsimod.wdl.messages.onGuiClosedWarning.unhandledTileEntity",
                                            pos, sender.getTileEntity(pos));
                                }
                            }

                            PROFILER.endSection();  // onItemGuiClosed
                        } else {
                            PROFILER.startSection("onItemGuiOpened");
                            PROFILER.startSection("Core");
                            WDLEvents.onItemGuiOpened();
                            PROFILER.endSection();  // "Core"
                            PROFILER.endSection();  // "onItemGuiOpened"
                        }

                        WDL.windowContainer = WDL.thePlayer.openContainer;
                    }
                }
                PROFILER.endSection();  // "inventoryCheck"
            }

            PROFILER.startSection("capes");
            CapeHandler.onWorldTick(players);
            PROFILER.endSection();  // "capes"
            PROFILER.endSection();  // "net.daporkchop.pepsimod.wdl"
        } catch (Throwable e) {
            WDL.crashed(e, "WDL mod: exception in onWorldClientTick event");
        }
    }

    /**
     * Called when {@link WorldClient#doPreChunk(int, int, boolean)} is called.
     * <br/>
     * Should be at the start of the method.
     */
    public static void onWorldClientDoPreChunk(WorldClient sender, int x,
                                               int z, boolean loading) {
        try {
            if (!WDL.downloading) {
                return;
            }

            PROFILER.startSection("net/daporkchop/pepsimod/wdl");

            if (!loading) {
                PROFILER.startSection("onChunkNoLongerNeeded");
                Chunk c = sender.getChunkFromChunkCoords(x, z);

                PROFILER.startSection("Core");
                net.daporkchop.pepsimod.wdl.WDLEvents.onChunkNoLongerNeeded(c);
                PROFILER.endSection();  // "Core"

                PROFILER.endSection();  // "onChunkNoLongerNeeded"
            } else {
                LOGGER.debug("Adding new empty chunk at " + x + ", " + z + " (already has: " + (sender.getChunkProvider().getLoadedChunk(x, z) != null) + ")");
            }

            PROFILER.endSection();
        } catch (Throwable e) {
            WDL.crashed(e, "WDL mod: exception in onWorldDoPreChunk event");
        }
    }

    /**
     * Called when {@link WorldClient#removeEntityFromWorld(int)} is called.
     * <br/>
     * Should be at the start of the method.
     *
     * @param eid The entity's unique ID.
     */
    public static void onWorldClientRemoveEntityFromWorld(WorldClient sender,
                                                          int eid) {
        try {
            if (!WDL.downloading) {
                return;
            }

            PROFILER.startSection("net.daporkchop.pepsimod.wdl.onRemoveEntityFromWorld");

            Entity entity = sender.getEntityByID(eid);

            PROFILER.startSection("Core");
            WDLEvents.onRemoveEntityFromWorld(entity);
            PROFILER.endSection();  // "Core"

            PROFILER.endSection();  // "net.daporkchop.pepsimod.wdl.onRemoveEntityFromWorld"
        } catch (Throwable e) {
            WDL.crashed(e,
                    "WDL mod: exception in onWorldRemoveEntityFromWorld event");
        }
    }

    /**
     * Called when {@link NetHandlerPlayClient#handleChat(SPacketChat)} is
     * called.
     * <br/>
     * Should be at the end of the method.
     */
    public static void onNHPCHandleChat(NetHandlerPlayClient sender,
                                        SPacketChat packet) {
        try {
            if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                return;
            }

            if (!WDL.downloading) {
                return;
            }

            PROFILER.startSection("net.daporkchop.pepsimod.wdl.onChatMessage");

            //func_148915_c returns the ITextComponent.
            String chatMessage = packet.getChatComponent().getUnformattedText();

            PROFILER.startSection("Core");
            WDLEvents.onChatMessage(chatMessage);
            PROFILER.endSection();  // "Core"

            for (ModInfo<IChatMessageListener> info : WDLApi
                    .getImplementingExtensions(IChatMessageListener.class)) {
                PROFILER.startSection(info.id);
                info.mod.onChat(WDL.worldClient, chatMessage);
                PROFILER.endSection();  // info.id
            }

            PROFILER.endSection();  // "net.daporkchop.pepsimod.wdl.onChatMessage"
        } catch (Throwable e) {
            WDL.crashed(e, "WDL mod: exception in onNHPCHandleChat event");
        }
    }

    /**
     * Called when {@link NetHandlerPlayClient#handleMaps(SPacketMaps)} is
     * called.
     * <br/>
     * Should be at the end of the method.
     */
    public static void onNHPCHandleMaps(NetHandlerPlayClient sender,
                                        SPacketMaps packet) {
        try {
            if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                return;
            }

            if (!WDL.downloading) {
                return;
            }

            PROFILER.startSection("net.daporkchop.pepsimod.wdl.onMapDataLoaded");

            int id = packet.getMapId();
            MapData mapData = ItemMap.loadMapData(packet.getMapId(),
                    WDL.worldClient);

            PROFILER.startSection("Core");
            WDLEvents.onMapDataLoaded(id, mapData);
            PROFILER.endSection();  // "Core"

            PROFILER.endSection();  // "net.daporkchop.pepsimod.wdl.onMapDataLoaded"
        } catch (Throwable e) {
            WDL.crashed(e, "WDL mod: exception in onNHPCHandleMaps event");
        }
    }

    /**
     * Called when
     * {@link NetHandlerPlayClient#handleCustomPayload(SPacketCustomPayload)}
     * is called.
     * <br/>
     * Should be at the end of the method.
     */
    public static void onNHPCHandleCustomPayload(NetHandlerPlayClient sender,
                                                 SPacketCustomPayload packet) {
        try {
            if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                return;
            }
            if (!packet.getBufferData().isReadable()) {
                return;
            }
            PROFILER.startSection("net.daporkchop.pepsimod.wdl.onPluginMessage");

            PROFILER.startSection("Parse");
            String channel = packet.getChannelName();
            ByteBuf buf = packet.getBufferData();
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            PROFILER.endSection();  // "Parse"

            PROFILER.startSection("Core");
            WDLEvents.onPluginChannelPacket(channel, payload);
            PROFILER.endSection();  // "Core"

            for (ModInfo<IPluginChannelListener> info : WDLApi
                    .getImplementingExtensions(IPluginChannelListener.class)) {
                PROFILER.startSection(info.id);
                info.mod.onPluginChannelPacket(WDL.worldClient, channel,
                        payload);
                PROFILER.endSection();  // info.id
            }

            PROFILER.endSection();  // "net.daporkchop.pepsimod.wdl.onPluginMessage"
        } catch (Throwable e) {
            WDL.crashed(e,
                    "WDL mod: exception in onNHPCHandleCustomPayload event");
        }
    }

    /**
     * Called when
     * {@link NetHandlerPlayClient#handleBlockAction(SPacketBlockAction)} is
     * called.
     * <br/>
     * Should be at the end of the method.
     */
    public static void onNHPCHandleBlockAction(NetHandlerPlayClient sender,
                                               SPacketBlockAction packet) {
        try {
            if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                return;
            }

            if (!WDL.downloading) {
                return;
            }

            PROFILER.startSection("net.daporkchop.pepsimod.wdl.onBlockEvent");

            BlockPos pos = packet.getBlockPosition();
            Block block = packet.getBlockType();
            int data1 = packet.getData1();
            int data2 = packet.getData2();

            PROFILER.startSection("Core");
            WDLEvents.onBlockEvent(pos, block, data1, data2);
            PROFILER.endSection();  // "Core"

            for (ModInfo<IBlockEventListener> info : WDLApi
                    .getImplementingExtensions(IBlockEventListener.class)) {
                PROFILER.startSection(info.id);
                info.mod.onBlockEvent(WDL.worldClient, pos, block,
                        data1, data2);
                PROFILER.endSection();  // info.id
            }

            PROFILER.endSection();  // "net.daporkchop.pepsimod.wdl.onBlockEvent"
        } catch (Throwable e) {
            WDL.crashed(e,
                    "WDL mod: exception in onNHPCHandleBlockAction event");
        }
    }

    /**
     * Injects WDL information into a crash report.
     * <p>
     * Called at the end of {@link CrashReport#populateEnvironment()}.
     *
     * @param report
     */
    public static void onCrashReportPopulateEnvironment(CrashReport report) {
        WDL.addInfoToCrash(report);
    }

    /**
     * Adds the "Download this world" button to the ingame pause GUI.
     *
     * @param gui
     * @param buttonList
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void injectWDLButtons(GuiIngameMenu gui, List buttonList) {
        int insertAtYPos = 0;

        for (Object obj : buttonList) {
            GuiButton btn = (GuiButton) obj;

            if (btn.id == 5) { // Button "Achievements"
                insertAtYPos = btn.y + 24;
                break;
            }
        }

        // Move other buttons down one slot (= 24 height units)
        for (Object obj : buttonList) {
            GuiButton btn = (GuiButton) obj;

            if (btn.y >= insertAtYPos) {
                btn.y += 24;
            }
        }

        // Insert net.daporkchop.pepsimod.wdl buttons.
        GuiButton wdlDownload = new GuiButton(WDLs, gui.width / 2 - 100,
                insertAtYPos, 170, 20, null);

        GuiButton wdlOptions = new GuiButton(WDLo, gui.width / 2 + 71,
                insertAtYPos, 28, 20,
                I18n.format("net.daporkchop.pepsimod.wdl.gui.ingameMenu.settings"));

        if (WDL.minecraft.isIntegratedServerRunning()) {
            wdlDownload.displayString = I18n
                    .format("net.daporkchop.pepsimod.wdl.gui.ingameMenu.downloadStatus.singlePlayer");
            wdlDownload.enabled = false;
        } else if (!WDLPluginChannels.canDownloadAtAll()) {
            if (WDLPluginChannels.canRequestPermissions()) {
                // Allow requesting permissions.
                wdlDownload.displayString = I18n
                        .format("net.daporkchop.pepsimod.wdl.gui.ingameMenu.downloadStatus.request");
            } else {
                // Out of date plugin :/
                wdlDownload.displayString = I18n
                        .format("net.daporkchop.pepsimod.wdl.gui.ingameMenu.downloadStatus.disabled");
                wdlDownload.enabled = false;
            }
        } else if (WDL.saving) {
            wdlDownload.displayString = I18n
                    .format("net.daporkchop.pepsimod.wdl.gui.ingameMenu.downloadStatus.saving");
            wdlDownload.enabled = false;
            wdlOptions.enabled = false;
        } else if (WDL.downloading) {
            wdlDownload.displayString = I18n
                    .format("net.daporkchop.pepsimod.wdl.gui.ingameMenu.downloadStatus.stop");
        } else {
            wdlDownload.displayString = I18n
                    .format("net.daporkchop.pepsimod.wdl.gui.ingameMenu.downloadStatus.start");
        }
        buttonList.add(wdlDownload);
        buttonList.add(wdlOptions);
    }

    /**
     * Handle clicks in the ingame pause GUI.
     *
     * @param gui
     * @param button
     */
    public static void handleWDLButtonClick(GuiIngameMenu gui, GuiButton button) {
        if (!button.enabled) {
            return;
        }

        if (button.id == WDLs) { // "Start/Stop Download"
            if (WDL.minecraft.isIntegratedServerRunning()) {
                return; // WDL not available if in singleplayer or LAN server mode
            }

            if (WDL.downloading) {
                WDL.stopDownload();
            } else {
                if (!WDLPluginChannels.canDownloadAtAll()) {
                    // If they don't have any permissions, let the player
                    // request some.
                    if (WDLPluginChannels.canRequestPermissions()) {
                        WDL.minecraft.displayGuiScreen(new GuiWDLPermissions(gui));
                    } else {
                        button.enabled = false;
                    }

                    return;
                } else if (WDLPluginChannels.hasChunkOverrides()
                        && !WDLPluginChannels.canDownloadInGeneral()) {
                    // Handle the "only has chunk overrides" state - notify
                    // the player of limited areas.
                    WDL.minecraft.displayGuiScreen(new GuiWDLChunkOverrides(gui));
                } else {
                    WDL.startDownload();
                }
            }
        } else if (button.id == WDLo) { // "..." (options)
            if (WDL.minecraft.isIntegratedServerRunning()) {
                WDL.minecraft.displayGuiScreen(new GuiWDLAbout(gui));
            } else {
                WDL.minecraft.displayGuiScreen(new GuiWDL(gui));
            }
        } else if (button.id == 1) { // "Disconnect"
            WDL.stopDownload();
        }
    }
}
