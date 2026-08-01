package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleApiaristAnalyser;

public class ModuleBeeAnalyzerMuiDynamic extends GenericModuleMUI<ModuleApiaristAnalyser> {

    public ModuleBeeAnalyzerMuiDynamic(ModuleApiaristAnalyser module) {
        super(module);
    }

    @Override
    public String getId() {
        return "module_bee_analyzer";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        BooleanSyncValue extractModeSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(
                        getFullId() + "_extract_mode",
                        BooleanSyncValue.class,
                        () -> new BooleanSyncValue(module::getExtractModeBool, module::setExtractMode))
                : new BooleanSyncValue(module::getExtractModeBool, module::setExtractMode);

        widget.child(
                new CycleButtonWidget().value(extractModeSync).overlay(
                        IKey.lang(() -> module.getExtractModeBool() ? "Extract items: On" : "Extract items: Off"))
                        .width(120).height(22).align(Alignment.CENTER));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public int getWidth() {
        return 140;
    }

    @Override
    public int getHeight() {
        return 80;
    }
}
