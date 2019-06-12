package org.eclipse.jemo.sys;

import org.eclipse.jemo.api.BatchModule;
import org.eclipse.jemo.api.Frequency;
import org.eclipse.jemo.api.ModuleLimit;
import org.eclipse.jemo.internal.model.JemoApplicationMetaData;
import org.eclipse.jemo.sys.internal.SystemDB;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jemo.sys.JemoPluginManager.MODULE_METADATA_TABLE;

/**
 * Runs once per 10 minutes and flushes to the database the performance statistics per running module.
 *
 * @author Yannis Theocharis
 */
public class ModulesStatsModule implements BatchModule {

    private final Map<String, JemoApplicationMetaData> knownApplications;

    public ModulesStatsModule(Map<String, JemoApplicationMetaData> knownApplications) {
        this.knownApplications = knownApplications;
    }

    @Override
    public void processBatch(String location, boolean isCloudLocation) throws Throwable {
        knownApplications.values().forEach(appMetaData -> SystemDB.save(MODULE_METADATA_TABLE, appMetaData));
    }

    @Override
    public ModuleLimit getLimits() {
        return ModuleLimit.newInstance()
                .setMaxActiveBatchesPerGSM(1)
                .setBatchFrequency(Frequency.of(TimeUnit.MINUTES, 10))
                .build();
    }
}
