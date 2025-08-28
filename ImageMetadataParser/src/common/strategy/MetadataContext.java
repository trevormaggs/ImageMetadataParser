package common.strategy;

public class MetadataContext
{
    private MetadataStrategy<?> strategy;

    public MetadataContext(MetadataStrategy<?> strategy)
    {
        this.strategy = strategy;
    }
}
