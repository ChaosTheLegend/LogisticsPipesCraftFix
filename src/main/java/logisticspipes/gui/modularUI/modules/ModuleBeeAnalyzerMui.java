package logisticspipes.gui.modularUI.modules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import logisticspipes.gui.modularUI.LogisticsModuleMUI;
import logisticspipes.modules.ModuleApiaristAnalyser;
import logisticspipes.modules.abstractmodules.LogisticsModule;

import java.util.HashMap;
import java.util.Map;

public class ModuleBeeAnalyzerMui extends LogisticsModuleMUI {


    public ModuleBeeAnalyzerMui(LogisticsModule module) {
        super(module);
    }

    @Override
    public String getId() {
        return "module_bee_analyzer";
    }

    private enum ExtractMode {
        OFF(0),
        ON(1);

        private int value;

        ExtractMode(int i) {
            value = i;
        }

        private static Map map = new HashMap<>();

        static {
            for (ExtractMode mode : values()) {
                map.put(mode.ordinal(), mode);
            }
        }

        static ExtractMode valueOf(int i) {
            return (ExtractMode) map.get(i);
        }

        public int getValue() {
            return value;
        }
    }
    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        ModuleApiaristAnalyser apiaristAnalyser = (ModuleApiaristAnalyser) module;

        widget
            .child(new CycleButtonWidget()
                .value(SyncHandlers.bool(apiaristAnalyser::getExtractModeBool, apiaristAnalyser::setExtractMode))
                .overlay(IKey.lang(
                    () -> apiaristAnalyser.getExtractModeBool() ?
                        "Extract items: On" :
                        "Extract items: Off")
                    )
                .width(120).height(22)
                .align(Alignment.CENTER)
            );

        return widget;
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
