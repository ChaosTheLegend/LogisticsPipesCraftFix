package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ColorType;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.pipes.PipeCraftingChassi;
import net.minecraft.util.ResourceLocation;

public class GuiCraftingChassis {

    private PipeCraftingChassi _targetPipe;

    private static final ResourceLocation MODULE_ICON = new ResourceLocation("logisticspipes","textures/gui/ModuleGhost.png");
    private static final ResourceLocation GHOST_UPGRADE = new ResourceLocation("logisticspipes","textures/gui/GhostUpgrade.png");

    public GuiCraftingChassis(PipeCraftingChassi targetPipe) {
        _targetPipe = targetPipe;
    }

    public void addWidgets(ModularPanel panel, PosGuiData data, PanelSyncManager syncManager) {
        panel
            .pos(310, 147)
                // Blocking Mode
                .background(ModularUIHelper.BACKGROUND_TEXTURE).bindPlayerInventory()
                .child(new Column()
                    .width(20)
                    .left(-22)
                    .child(new CycleButtonWidget().tooltipBuilder(tooltip -> {
                    tooltip.addLine("Blocking mode:");
                    switch (_targetPipe.blockMode) {
                        case OFF:
                            tooltip.addLine("Blocking mode: Off");
                            break;
                        case NORMAL:
                            tooltip.addLine("Blocking mode: Normal");
                            break;
                        case SMART:
                            tooltip.addLine("Blocking mode: Smart");
                            break;
                    }
                })
                        .overlay(IKey.dynamic(() -> switch (_targetPipe.blockMode) {
                        case OFF -> "X";
                        case NORMAL -> "B";
                        case SMART -> "S";
                        }))
                        .width(20)
                        .height(20)
                        .tooltipPos(RichTooltip.Pos.ABOVE)
                        .value(
                                SyncHandlers.enumValue(
                                PipeCraftingChassi.BlockingModeState.class,
                                () -> { return _targetPipe.blockMode; },
                                blockingModeState -> _targetPipe.blockMode = blockingModeState))

                    )
                    .childPadding(2)
                    .child(new ButtonWidget<>()
                        .width(20)
                        .height(20)
                        .overlay(IKey.str("x2")
                        )))
                // Main Gui
                .child(
                        new Column().widthRel(1.0f).top(6).coverChildrenHeight()
                                .child(IKey.str("Crafting Chassis MK" + _targetPipe.getChassisTier()).asWidget())
                                .child(
                                        SlotGroupWidget.builder().row("IIIIIIIII")
                                                .key('I', i -> new ItemSlot().slot(_targetPipe.getBufferInventory(), i))
                                                .build())
                                .childPadding(4).child(
                                        SlotGroupWidget.builder().row("IIIIIIIII").row("IIIIIIIII").row("IIIIIIIII")
                                                .row("IIIIIIIII")
                                                .key(
                                                        'I',
                                                        i -> {
                                                            ItemSlot slot = new ItemSlot().slot(_targetPipe.getPatternInventory(), i);
                                                            var row = i/9;
                                                            if(row > _targetPipe.getChassisTier()-1){
                                                                slot.overlay(new Rectangle().setColor(0, 90,0,0));
                                                            }
                                                            return slot;
                                                            })
                                                .build()))
                // Upgrades
                .child(new Column()
                    .background(ModularUIHelper.BACKGROUND_TEXTURE).width(26).right(-28)
                    .child(SlotGroupWidget.builder()
                        .row("I")
                        .row("I")
                        .row("I")
                        .row("I")
                        .key('I', i -> new ItemSlot()
                            .slot(_targetPipe.getUpgradeInventory(), i))
                        .build()
                    )
                    .padding(4)
                    .coverChildrenHeight())
                ;

    }

}
