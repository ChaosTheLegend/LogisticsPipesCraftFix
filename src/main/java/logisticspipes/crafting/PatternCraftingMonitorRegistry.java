package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LinkedLogisticsOrderList;

public final class PatternCraftingMonitorRegistry {

    private static final Map<IOrderInfoProvider, PatternCraftingOrder> STAGED_ORDERS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private PatternCraftingMonitorRegistry() {}

    /**
     * Registers the live output order that represents a staged pattern crafting order.
     */
    static void register(IOrderInfoProvider outputOrder, PatternCraftingOrder order) {
        if (outputOrder != null && order != null) {
            STAGED_ORDERS.put(outputOrder, order);
        }
    }

    /**
     * Looks up staged crafting state by its live output order reference.
     */
    static PatternCraftingOrder find(IOrderInfoProvider outputOrder) {
        if (outputOrder == null) {
            return null;
        }
        return STAGED_ORDERS.get(outputOrder);
    }

    /**
     * Builds monitor roots for every staged pattern order in a watched request tree.
     */
    public static List<PatternCraftingMonitorNode> build(LinkedLogisticsOrderList orders) {
        if (orders == null) {
            return Collections.emptyList();
        }
        cleanupFinishedOrders();
        List<PatternCraftingMonitorNode> result = new ArrayList<>();
        appendMonitorNodes(orders, result);
        return result;
    }

    private static void appendMonitorNodes(LinkedLogisticsOrderList orders, List<PatternCraftingMonitorNode> result) {
        for (IOrderInfoProvider order : orders) {
            PatternCraftingOrder stagedOrder = find(order);
            if (stagedOrder == null) {
                continue;
            }
            PatternCraftingMonitorNode node = stagedOrder.toMonitorNode(
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            if (node.hasVisibleWork()) {
                result.add(node);
            }
        }
        for (LinkedLogisticsOrderList subOrder : orders.getSubOrders()) {
            appendMonitorNodes(subOrder, result);
        }
    }

    private static void cleanupFinishedOrders() {
        synchronized (STAGED_ORDERS) {
            STAGED_ORDERS.keySet().removeIf(order -> order == null || order.isFinished() && order.getProgresses().isEmpty());
        }
    }
}
