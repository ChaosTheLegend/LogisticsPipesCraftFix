package logisticspipes.crafting.requesttable.upgrades;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.IPipeUpgrade;

/**
 * Base implementation for upgrades that only apply to the redesigned request table.
 */
abstract class RequestTableStorageUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return true;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return pipe instanceof RequestTablePipe;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
        return false;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "newRequestTable" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] {};
    }
}
