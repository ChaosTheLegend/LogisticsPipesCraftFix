package logisticspipes.gui.modularUI.modules;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public abstract class LogisticsPipeMUI extends LogisticsModularUI {

    protected final CoreRoutedPipe pipe;

    public CoreRoutedPipe getPipe() {return pipe;}

    public LogisticsPipeMUI(CoreRoutedPipe pipe) {
        this.pipe = pipe;
    }
}
