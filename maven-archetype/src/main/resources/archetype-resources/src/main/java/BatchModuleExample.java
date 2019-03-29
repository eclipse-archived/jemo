package ${package};

import org.eclipse.jemo.api.BatchModule;
import org.eclipse.jemo.api.Frequency;
import org.eclipse.jemo.api.ModuleLimit;

import java.util.concurrent.TimeUnit;

public class BatchModuleExample implements BatchModule {

    @Override
    public void processBatch(String location, boolean isCloudLocation) throws Throwable {
        // TODO: Add code here
    }

    @Override
    public ModuleLimit getLimits() {
        // Select how many batch processes run per instance, location or GSM (i.e. across all instances).
        // Also, select how frequently it runs.
        // If you don't override the getLimits method, the default behaviour is 1 batch process per location running every 1 minute.
        return ModuleLimit.newInstance()
                .setMaxActiveBatchesPerGSM(1)
                .setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 30))
                .build();
    }
}