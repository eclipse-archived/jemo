package ${package};

import org.eclipse.jemo.api.FixedModule;
import org.eclipse.jemo.api.ModuleLimit;

public class FixedModuleExample implements FixedModule {

    @Override
    public void processFixed(String location, String instanceId) throws Throwable {
        // TODO: Add code here
    }

    @Override
    public ModuleLimit getLimits() {
        // TODO: Select how many fixed processes run per instance, location or GSM (i.e. across all instances).
        // If you don't override the getLimits method, the default behaviour is at max 1 batch process per instance, location and GSM.
        return ModuleLimit.newInstance()
                .setMaxActiveFixedPerInstance(1)
                .build();
    }
}