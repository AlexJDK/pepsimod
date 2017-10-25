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

import net.daporkchop.pepsimod.wdl.MessageTypeCategory;
import net.daporkchop.pepsimod.wdl.WDL;
import net.daporkchop.pepsimod.wdl.WDLMessages;
import net.daporkchop.pepsimod.wdl.api.IWDLMessageType;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GuiWDLMessages extends GuiScreen {
    /**
     * Set from inner classes; this is the text to draw.
     */
    private String hoveredButtonDescription = null;
    private GuiScreen parent;
    private GuiMessageTypeList list;
    private GuiButton enableAllButton;
    private GuiButton resetButton;

    public GuiWDLMessages(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        enableAllButton = new GuiButton(100, (this.width / 2) - 155, 18, 150,
                20, getAllEnabledText());
        this.buttonList.add(enableAllButton);
        resetButton = new GuiButton(101, (this.width / 2) + 5, 18, 150, 20,
                I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.reset"));
        this.buttonList.add(resetButton);

        this.list = new GuiMessageTypeList();

        this.buttonList.add(new GuiButton(102, (this.width / 2) - 100,
                this.height - 29, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }

        if (button.id == 100) {
            //"Master switch"
            WDLMessages.enableAllMessages ^= true;

            WDL.baseProps.setProperty("Messages.enableAll",
                    Boolean.toString(WDLMessages.enableAllMessages));

            button.displayString = getAllEnabledText();
        } else if (button.id == 101) {
            this.mc.displayGuiScreen(new GuiYesNo(this,
                    I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.reset.confirm.title"),
                    I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.reset.confirm.subtitle"),
                    101));
        } else if (button.id == 102) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result) {
            if (id == 101) {
                WDLMessages.resetEnabledToDefaults();
            }
        }

        mc.displayGuiScreen(this);
    }

    @Override
    public void onGuiClosed() {
        WDL.saveProps();
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton)
            throws IOException {
        if (list.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (list.mouseReleased(mouseX, mouseY, state)) {
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hoveredButtonDescription = null;

        this.drawDefaultBackground();
        this.list.drawScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(this.fontRenderer,
                I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.message.title"),
                this.width / 2, 8, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (hoveredButtonDescription != null) {
            Utils.drawGuiInfoBox(hoveredButtonDescription, width, height, 48);
        } else if (enableAllButton.isMouseOver()) {
            Utils.drawGuiInfoBox(
                    I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.all.description"), width,
                    height, 48);
        } else if (resetButton.isMouseOver()) {
            Utils.drawGuiInfoBox(
                    I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.reset.description"), width,
                    height, 48);
        }
    }

    /**
     * Gets the text for the "Enable all" button.
     */
    private String getAllEnabledText() {
        return I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.all."
                + WDLMessages.enableAllMessages);
    }

    private class GuiMessageTypeList extends GuiListExtended {
        // The call to Stream.concat is somewhat hacky, but hard to avoid
        // (we want both a header an the items in it)
        private List<GuiListEntry> entries = WDLMessages
                .getTypes().asMap().entrySet().stream()
                .flatMap(e -> Stream.concat(
                        Stream.of(new CategoryEntry(e.getKey())),
                        e.getValue().stream().map(t -> new MessageTypeEntry(t, e.getKey()))))
                .collect(Collectors.toList());

        public GuiMessageTypeList() {
            super(GuiWDLMessages.this.mc, GuiWDLMessages.this.width,
                    GuiWDLMessages.this.height, 39,
                    GuiWDLMessages.this.height - 32, 20);
        }

        @Override
        public IGuiListEntry getListEntry(int index) {
            return entries.get(index);
        }

        @Override
        protected int getSize() {
            return entries.size();
        }

        private class CategoryEntry extends GuiListEntry {
            private final GuiButton button;
            private final MessageTypeCategory category;

            public CategoryEntry(MessageTypeCategory category) {
                this.category = category;
                this.button = new GuiButton(0, 0, 0, 80, 20, "");
            }

            @Override
            public void drawEntry(int slotIndex, int x, int y, int listWidth,
                                  int slotHeight, int mouseX, int mouseY, boolean isSelected) {
                drawCenteredString(fontRenderer, category.getDisplayName(),
                        GuiWDLMessages.this.width / 2 - 40, y + slotHeight
                                - mc.fontRenderer.FONT_HEIGHT - 1, 0xFFFFFF);

                button.x = GuiWDLMessages.this.width / 2 + 20;
                button.y = y;

                button.displayString = I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.group."
                        + WDLMessages.isGroupEnabled(category));
                button.enabled = WDLMessages.enableAllMessages;

                LocalUtils.drawButton(this.button, mc, mouseX, mouseY);
            }

            @Override
            public boolean mousePressed(int slotIndex, int x, int y,
                                        int mouseEvent, int relativeX, int relativeY) {
                if (button.mousePressed(mc, x, y)) {
                    WDLMessages.toggleGroupEnabled(category);

                    button.playPressSound(mc.getSoundHandler());

                    return true;
                }

                return false;
            }

            @Override
            public void mouseReleased(int slotIndex, int x, int y,
                                      int mouseEvent, int relativeX, int relativeY) {

            }
        }

        private class MessageTypeEntry extends GuiListEntry {
            private final GuiButton button;
            private final IWDLMessageType type;
            private final MessageTypeCategory category;

            public MessageTypeEntry(IWDLMessageType type,
                                    MessageTypeCategory category) {
                this.type = type;
                this.button = new GuiButton(0, 0, 0, type.toString());
                this.category = category;
            }

            @Override
            public void drawEntry(int slotIndex, int x, int y, int listWidth,
                                  int slotHeight, int mouseX, int mouseY, boolean isSelected) {
                button.x = GuiWDLMessages.this.width / 2 - 100;
                button.y = y;

                button.displayString = I18n.format("net.daporkchop.pepsimod.wdl.gui.messages.message."
                        + WDLMessages.isEnabled(type), type.getDisplayName());
                button.enabled = WDLMessages.enableAllMessages &&
                        WDLMessages.isGroupEnabled(category);

                LocalUtils.drawButton(this.button, mc, mouseX, mouseY);

                if (button.isMouseOver()) {
                    hoveredButtonDescription = type.getDescription();
                }
            }

            @Override
            public boolean mousePressed(int slotIndex, int x, int y,
                                        int mouseEvent, int relativeX, int relativeY) {
                if (button.mousePressed(mc, x, y)) {
                    WDLMessages.toggleEnabled(type);

                    button.playPressSound(mc.getSoundHandler());

                    return true;
                }

                return false;
            }

            @Override
            public void mouseReleased(int slotIndex, int x, int y,
                                      int mouseEvent, int relativeX, int relativeY) {

            }
        }

    }
}
