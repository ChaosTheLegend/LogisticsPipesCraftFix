/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.gui;

import logisticspipes.utils.gui.ISearchBar;
import logisticspipes.utils.gui.SearchBar;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import logisticspipes.pipes.PipeFluidSatellite;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.string.StringUtils;

public class GuiSatellitePipe extends LogisticsBaseGuiScreen {

    private PipeItemsSatelliteLogistics _satellite;
    private PipeFluidSatellite _liquidSatellite;
    private final EntityPlayer _player;

    private ISearchBar satelliteIdField;
    public GuiSatellitePipe(PipeItemsSatelliteLogistics satellite, EntityPlayer player) {
        super(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer entityplayer) {
                return true;
            }
        });
        _satellite = satellite;
        _player = player;
        xSize = 116;
        ySize = 70;
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
        xSize = 116;
        ySize = 70;
    }

    @Override
    public void initGui() {
        super.initGui();

        buttonList.add(new GuiButton(0, (width / 2) - (30 / 2) + 35, (height / 2) - (20 / 2), 30, 20, "+"));
        buttonList.add(new GuiButton(1, (width / 2) - (30 / 2) - 35, (height / 2) - (20 / 2), 30, 20, "-"));

        if (satelliteIdField == null) {
            satelliteIdField = new SearchBar(mc.fontRenderer, this, xCenter - 25, bottom - 24, 50, 15, false, true, false);
            satelliteIdField.setContent(String.valueOf(_satellite == null ? 0 : _satellite.satelliteId));
        }
        satelliteIdField.reposition(xCenter - 25, yCenter - 10, 40, 15);
    }

    @Override
    protected void mouseClicked(int i, int j, int k) {
        satelliteIdField.handleClick(i, j, k);
        super.mouseClicked(i, j, k);
    }

    @Override
    public void handleMouseInputSub() {
        if (!satelliteIdField.isFocused()) {
            syncIdField();
        }
        super.handleMouseInputSub();
    }

    @Override
    protected void keyTyped(char c, int i) {
        // Track everything except Escape when in search bar
        if (i == 1 || (!satelliteIdField.handleKey(c, i))) {
            super.keyTyped(c, i);
        }
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        if (_satellite != null) {
            if (guibutton.id == 0) {
                _satellite.setNextId(_player);
                satelliteIdField.setContent(String.valueOf(_satellite.satelliteId));
            }

            if (guibutton.id == 1) {
                _satellite.setPrevId(_player);
                satelliteIdField.setContent(String.valueOf(_satellite.satelliteId));
            }
            super.actionPerformed(guibutton);
        } else if (_liquidSatellite != null) {
            if (guibutton.id == 0) {
                _liquidSatellite.setNextId(_player);
                satelliteIdField.setContent(String.valueOf(_liquidSatellite.satelliteId));
            }

            if (guibutton.id == 1) {
                _liquidSatellite.setPrevId(_player);
                satelliteIdField.setContent(String.valueOf(_liquidSatellite.satelliteId));
            }
            super.actionPerformed(guibutton);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {

        satelliteIdField.renderSearchBar();
        if (!satelliteIdField.isFocused()) {
            syncIdField();
        }

        super.drawGuiContainerForegroundLayer(par1, par2);
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/satellite.png");

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.renderEngine.bindTexture(GuiSatellitePipe.TEXTURE);
        int j = guiLeft;
        int k = guiTop;
        drawTexturedModalRect(j, k, 0, 0, xSize, ySize);
    }

    private void syncIdField() {
        if(_satellite != null) {
            try {
                int id = Integer.parseInt(satelliteIdField.getContent());
                _satellite.setId(_player, id);
            } catch (NumberFormatException e) {
                satelliteIdField.setContent(String.valueOf(_satellite.satelliteId));
            }
        }
        else if(_liquidSatellite != null) {
            try {
                int id = Integer.parseInt(satelliteIdField.getContent());
                _liquidSatellite.setId(_player, id);
            } catch (NumberFormatException e) {
                satelliteIdField.setContent(String.valueOf(_liquidSatellite.satelliteId));
            }
        }
    }
}
