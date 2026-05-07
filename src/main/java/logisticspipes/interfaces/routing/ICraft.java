package logisticspipes.interfaces.routing;

import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.IResource;

public interface ICraft extends IProvide, IRequest {

    void registerExtras(IPromise promise);

    /**
     * Confusing name, it converts the resource into a crafting template if pipe has one this is used for RequestTree to
     * determine if a pipe can craft a certain item
     * 
     * @param type item to craft
     * @return template for the given item
     */
    ICraftingTemplate addCrafting(IResource type);

    boolean canCraft(IResource toCraft);

    int getTodo();
}
