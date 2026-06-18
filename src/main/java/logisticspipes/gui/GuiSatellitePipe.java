/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.gui;

import logisticspipes.crafting.PipeFluidPatternSatelliteLogistics;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.satpipe.PatternSatelliteSetName;
import logisticspipes.pipes.PipeFluidSatellite;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.string.StringUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class GuiSatellitePipe extends LogisticsBaseGuiScreen {

    private static final int NEXT_ID_BUTTON = 0;
    private static final int PREVIOUS_ID_BUTTON = 1;
    private static final int SAVE_NAME_BUTTON = 2;

    private PipeItemsSatelliteLogistics _satellite;
    private PipeFluidSatellite _liquidSatellite;
    private final EntityPlayer _player;
    private GuiTextField nameField;
    private boolean patternNameDirty;

    public GuiSatellitePipe(PipeItemsSatelliteLogistics satellite, EntityPlayer player) {
        super(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer entityplayer) {
                return true;
            }
        });
        _satellite = satellite;
        _player = player;
        configureSize();
    }

    public GuiSatellitePipe(PipeFluidSatellite satellite, EntityPlayer player) {
        super(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer entityplayer) {
                return true;
            }
        });
        _liquidSatellite = satellite;
        _player = player;
        configureSize();
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();

        if (isPatternSatellite()) {
            String currentName = nameField != null && patternNameDirty ? nameField.getText()
                : getPatternSatelliteName();
            nameField = new GuiTextField(fontRendererObj, guiLeft + 14, guiTop + 48, 104, 14);
            nameField.setMaxStringLength(64);
            nameField.setText(currentName);
            nameField.setFocused(true);
            buttonList.add(
                new SmallGuiButton(
                    SAVE_NAME_BUTTON,
                    guiLeft + 124,
                    guiTop + 50,
                    38,
                    10,
                    StringUtils.translate("gui.satellite.Save")));
            updatePatternNameButton();
            return;
        }

        buttonList
            .add(new GuiButton(NEXT_ID_BUTTON, (width / 2) - (30 / 2) + 35, (height / 2) - (20 / 2), 30, 20, "+"));
        buttonList.add(
            new GuiButton(PREVIOUS_ID_BUTTON, (width / 2) - (30 / 2) - 35, (height / 2) - (20 / 2), 30, 20, "-"));
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        if (isPatternSatellite()) {
            if (guibutton.id == SAVE_NAME_BUTTON) {
                savePatternSatelliteName();
            }
            super.actionPerformed(guibutton);
            return;
        }

        if (_satellite != null) {
            if (guibutton.id == NEXT_ID_BUTTON) {
                _satellite.setNextId(_player);
            }

            if (guibutton.id == PREVIOUS_ID_BUTTON) {
                _satellite.setPrevId(_player);
            }
            super.actionPerformed(guibutton);
        } else if (_liquidSatellite != null) {
            if (guibutton.id == NEXT_ID_BUTTON) {
                _liquidSatellite.setNextId(_player);
            }

            if (guibutton.id == PREVIOUS_ID_BUTTON) {
                _liquidSatellite.setPrevId(_player);
            }
            super.actionPerformed(guibutton);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (nameField == null) {
            return;
        }
        nameField.updateCursorCounter();
        if (!patternNameDirty) {
            String satelliteName = getPatternSatelliteName();
            if (!satelliteName.equals(nameField.getText())) {
                nameField.setText(satelliteName);
            }
        }
        updatePatternNameButton();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (nameField != null) {
            nameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typed, int keyCode) {
        if (nameField != null) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                savePatternSatelliteName();
                return;
            }
            if (nameField.textboxKeyTyped(typed, keyCode)) {
                patternNameDirty = true;
                updatePatternNameButton();
                return;
            }
        }
        super.keyTyped(typed, keyCode);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        super.drawGuiContainerForegroundLayer(par1, par2);
        if (isPatternSatellite()) {
            String title = StringUtils.translate("gui.satellite.PatternSatellite");
            mc.fontRenderer.drawString(title, xSize / 2 - mc.fontRenderer.getStringWidth(title) / 2, 8, 0x404040);
            mc.fontRenderer.drawString(
                StringUtils.translate("gui.satellite.SatelliteID") + ": " + getSatelliteId(),
                14,
                24,
                0x404040);
            mc.fontRenderer.drawString(StringUtils.translate("gui.satellite.Name"), 14, 38, 0x404040);
            return;
        }

        mc.fontRenderer.drawString(StringUtils.translate("gui.satellite.SatelliteID"), 33, 10, 0x404040);
        if (_satellite != null) {
            mc.fontRenderer.drawString(
                    _satellite.satelliteId + "",
                    59 - mc.fontRenderer.getStringWidth(_satellite.satelliteId + "") / 2,
                    31,
                    0x404040);
        }
        if (_liquidSatellite != null) {
            mc.fontRenderer.drawString(
                    _liquidSatellite.satelliteId + "",
                    59 - mc.fontRenderer.getStringWidth(_liquidSatellite.satelliteId + "") / 2,
                    31,
                    0x404040);
        }
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/satellite.png");

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        if (isPatternSatellite()) {
            GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
            if (nameField != null) {
                nameField.drawTextBox();
            }
            return;
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.renderEngine.bindTexture(GuiSatellitePipe.TEXTURE);
        int j = guiLeft;
        int k = guiTop;
        drawTexturedModalRect(j, k, 0, 0, xSize, ySize);
    }

    private void configureSize() {
        if (isPatternSatellite()) {
            xSize = 176;
            ySize = 88;
        } else {
            xSize = 116;
            ySize = 70;
        }
    }

    private boolean isPatternSatellite() {
        return _satellite instanceof PipeItemsPatternSatelliteLogistics
            || _liquidSatellite instanceof PipeFluidPatternSatelliteLogistics;
    }

    private int getSatelliteId() {
        if (_satellite != null) {
            return _satellite.satelliteId;
        }
        return _liquidSatellite == null ? 0 : _liquidSatellite.satelliteId;
    }

    private String getPatternSatelliteName() {
        if (_satellite instanceof PipeItemsPatternSatelliteLogistics) {
            return ((PipeItemsPatternSatelliteLogistics) _satellite).getSatelliteName();
        }
        if (_liquidSatellite instanceof PipeFluidPatternSatelliteLogistics) {
            return ((PipeFluidPatternSatelliteLogistics) _liquidSatellite).getSatelliteName();
        }
        return "";
    }

    private void setPatternSatelliteName(String name) {
        if (_satellite instanceof PipeItemsPatternSatelliteLogistics) {
            ((PipeItemsPatternSatelliteLogistics) _satellite).setSatelliteName(name);
        } else if (_liquidSatellite instanceof PipeFluidPatternSatelliteLogistics) {
            ((PipeFluidPatternSatelliteLogistics) _liquidSatellite).setSatelliteName(name);
        }
    }

    private void savePatternSatelliteName() {
        if (nameField == null) {
            return;
        }
        String name = nameField.getText().trim();
        setPatternSatelliteName(name);
        patternNameDirty = false;
        updatePatternNameButton();

        PatternSatelliteSetName packet = PacketHandler.getPacket(PatternSatelliteSetName.class);
        packet.setString(name);
        packet.setPosX(_satellite != null ? _satellite.getX() : _liquidSatellite.getX());
        packet.setPosY(_satellite != null ? _satellite.getY() : _liquidSatellite.getY());
        packet.setPosZ(_satellite != null ? _satellite.getZ() : _liquidSatellite.getZ());
        MainProxy.sendPacketToServer(packet);
    }

    private void updatePatternNameButton() {
        for (Object button : buttonList) {
            if (button instanceof GuiButton && ((GuiButton) button).id == SAVE_NAME_BUTTON) {
                ((GuiButton) button).enabled = patternNameDirty;
                return;
            }
        }
    }
}
