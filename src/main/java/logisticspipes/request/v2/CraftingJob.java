package logisticspipes.request.v2;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.interfaces.routing.IProvide;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.utils.item.ItemIdentifier;

public class CraftingJob implements IRequestJob {

    public JobStatus status;
    public final List<IRequestJob> subJobs = new ArrayList<>();

    public int amount = 0;
    public IProvideItems sender;
    public IOrderInfoProvider.ResourceType type;
    public ItemIdentifier item;
    public IRequestItems destination;

    public CraftingJob(IProvideItems sender, IOrderInfoProvider.ResourceType type, ItemIdentifier item,
            IRequestItems destination) {
        this.sender = sender;
        this.type = type;
        this.item = item;
        this.destination = destination;
        this.status = JobStatus.PENDING;
    }

    public CraftingJob(IPromise promise, IRequestItems destination) {
        this.destination = destination;
        this.status = JobStatus.PENDING;

        this.type = promise.getType();
        this.item = promise.getItemType();
        this.sender = (IProvideItems) promise.getProvider();
    }

    @Override
    public JobStatus getStatus() {
        return status;
    }

    @Override
    public List<IRequestJob> getSubJobs() {
        return subJobs;
    }

    @Override
    public boolean matches(IResource requestType) {
        return false;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public IExtraPromise split(int more) {
        amount -= more;
        return new LogisticsExtraPromise(getItemType(), more, sender, false);
    }

    @Override
    public IProvide getProvider() {
        return sender;
    }

    @Override
    public IRequestItems getDestination() {
        return destination;
    }

    @Override
    public ItemIdentifier getItemType() {
        return item;
    }

    @Override
    public IOrderInfoProvider.ResourceType getType() {
        return type;
    }
}
