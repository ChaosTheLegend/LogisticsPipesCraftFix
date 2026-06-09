package logisticspipes.gui.modularUI.modules;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.modules.abstractmodules.LogisticsModule;

public abstract class LogisticsModuleMUI extends LogisticsModularUI {

    protected final LogisticsModule module;

    public LogisticsModuleMUI(LogisticsModule module, String prefix) {
        super(prefix);
        this.module = module;
    }

    public LogisticsModuleMUI(LogisticsModule module) {
        this(module, "");
    }
}
