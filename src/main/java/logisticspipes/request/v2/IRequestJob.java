package logisticspipes.request.v2;

import java.util.List;

import logisticspipes.interfaces.routing.IProvide;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.utils.item.ItemIdentifier;

public interface IRequestJob {

    enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    JobStatus getStatus();

    List<IRequestJob> getSubJobs();

    boolean matches(IResource requestType);

    int getAmount();

    IExtraPromise split(int more);

    IProvide getProvider();

    IRequestItems getDestination();

    ItemIdentifier getItemType();

    IOrderInfoProvider.ResourceType getType();
}
