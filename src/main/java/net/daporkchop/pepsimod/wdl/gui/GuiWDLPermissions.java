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

package net.daporkchop.pepsimod.wdl.gui;

import io.netty.buffer.Unpooled;
import net.daporkchop.pepsimod.wdl.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * GUI that shows the current permissions for the user.
 */
public class GuiWDLPermissions extends GuiScreen {
    /**
     * Margins for the top and the bottom of the list.
     */
    private static final int TOP_MARGIN = 61, BOTTOM_MARGIN = 32;
    /**
     * Recalculates the {@link #globalEntries} list.
     */
    private final GuiScreen parent;
    /**
     * Reload permissions button
     */
    private GuiButton reloadButton;
    /**
     * Ticks (20ths of a second) until this UI needs to refresh.
     * <p>
     * If -1, don't refresh.
     */
    private int refreshTicks = -1;
    private TextList list;

    /**
     * Creates a new GUI with the given parent.
     *
     * @param parent
     */
    public GuiWDLPermissions(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        this.buttonList.add(new GuiButton(100, width / 2 - 100, height - 29,
                I18n.format("gui.done")));

        this.buttonList.add(new GuiButton(200, this.width / 2 - 155, 39, 100, 20,
                I18n.format("net.daporkchop.pepsimod.wdl.gui.permissions.current")));
        if (WDLPluginChannels.canRequestPermissions()) {
            this.buttonList.add(new GuiButton(201, this.width / 2 - 50, 39, 100, 20,
                    I18n.format("net.daporkchop.pepsimod.wdl.gui.permissions.request")));
            this.buttonList.add(new GuiButton(202, this.width / 2 + 55, 39, 100, 20,
                    I18n.format("net.daporkchop.pepsimod.wdl.gui.permissions.overrides")));
        }

        reloadButton = new GuiButton(1, (this.width / 2) + 5, 18, 150, 20,
                "Reload permissions");
        this.buttonList.add(reloadButton);

        this.list = new TextList(mc, width, height, TOP_MARGIN, BOTTOM_MARGIN);

        list.addLine("\u00A7c\u00A7lThis is a work in progress.");

        if (!WDLPluginChannels.hasPermissions()) {
            return;
        }

        list.addBlankLine();
        if (!WDLPluginChannels.canRequestPermissions()) {
            list.addLine("\u00A7cThe serverside permission plugin is out of date " +
                    "and does support permission requests.  Please go ask a " +
                    "server administrator to update the plugin.");
            list.addBlankLine();
        }

        if (WDLPluginChannels.getRequestMessage() != null) {
            list.addLine("Note from the server moderators: ");
            list.addLine(WDLPluginChannels.getRequestMessage());
            list.addBlankLine();
        }

        list.addLine("These are your current permissions:");
        // TODO: I'd like to return the description lines here, but can't yet.
        // Of course, I'd need to put in some better lines than before.
        // Maybe also skip unsent permissions?
        list.addLine("Can download: "
                + WDLPluginChannels.canDownloadInGeneral());
        list.addLine("Can save chunks as you move: " + WDLPluginChannels.canCacheChunks());
        if (!WDLPluginChannels.canCacheChunks() && WDLPluginChannels.canDownloadInGeneral()) {
            list.addLine("Nearby chunk save radius: " + WDLPluginChannels.getSaveRadius());
        }
        list.addLine("Can save entities: "
                + WDLPluginChannels.canSaveEntities());
        list.addLine("Can save tile entities: "
                + WDLPluginChannels.canSaveTileEntities());
        list.addLine("Can save containers: "
                + WDLPluginChannels.canSaveContainers());
        list.addLine("Received entity ranges: "
                + WDLPluginChannels.hasServerEntityRange() + " ("
                + WDLPluginChannels.getEntityRanges().size() + " total)");
    }

    @Override
    public void updateScreen() {
        if (refreshTicks > 0) {
            refreshTicks--;
        } else if (refreshTicks == 0) {
            initGui();
            refreshTicks = -1;
        }
        super.updateScreen();
    }

    @Override
    public void onGuiClosed() {
        WDL.saveProps();
    }

    /**
     * Called when the mouse is clicked.
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton)
            throws IOException {
        list.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Handles mouse input.
     */
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        this.list.handleMouseInput();
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (list.mouseReleased(mouseX, mouseY, state)) {
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            // Send the init packet.
            CPacketCustomPayload initPacket;
            try {
                String payload = "{\"X-RTFM\":\"http://wiki.vg/Plugin_channels/World_downloader\",\"X-UpdateNote\":\"The plugin message system will be changing shortly.  Please stay tuned.\",\"Version\":\"%s\",\"State\":\"Refresh?\"}";
                payload = String.format(payload, VersionConstants.getModVersion());
                initPacket = new CPacketCustomPayload("WDL|INIT",
                        new PacketBuffer(Unpooled.copiedBuffer(payload
                                .getBytes("UTF-8"))));
            } catch (UnsupportedEncodingException e) {
                WDLMessages.chatMessageTranslated(WDLMessageTypes.ERROR,
                        "net.daporkchop.pepsimod.wdl.messages.generalError.noUTF8", e);

                initPacket = new CPacketCustomPayload("WDL|INIT",
                        new PacketBuffer(Unpooled.buffer()));
            }
            Minecraft.getMinecraft().getConnection().sendPacket(initPacket);

            button.enabled = false;
            button.displayString = "Refreshing...";

            refreshTicks = 50; // 2.5 seconds
        }
        if (button.id == 100) {
            this.mc.displayGuiScreen(this.parent);
        }
        if (button.id == 200) {
            // Would open this GUI; do nothing.
        }
        if (button.id == 201) {
            this.mc.displayGuiScreen(new GuiWDLPermissionRequest(this.parent));
        }
        if (button.id == 202) {
            this.mc.displayGuiScreen(new GuiWDLChunkOverrides(this.parent));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.list == null) {
            return;
        }

        this.list.drawScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(this.fontRenderer, "Permission info",
                this.width / 2, 8, 0xFFFFFF);

        if (!WDLPluginChannels.hasPermissions()) {
            this.drawCenteredString(this.fontRenderer,
                    "No permissions received; defaulting to everything enabled.",
                    this.width / 2, (this.height - 32 - 23) / 2 + 23
                            - fontRenderer.FONT_HEIGHT / 2, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
