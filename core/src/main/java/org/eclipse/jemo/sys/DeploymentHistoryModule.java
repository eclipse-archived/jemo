package org.eclipse.jemo.sys;

import org.eclipse.jemo.api.BatchModule;
import org.eclipse.jemo.api.Frequency;
import org.eclipse.jemo.api.ModuleLimit;
import org.eclipse.jemo.sys.JemoRuntimeAdmin.DeployResource;
import org.eclipse.jemo.sys.internal.SystemDB;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static java.util.logging.Level.INFO;
import static org.eclipse.jemo.sys.JemoRuntimeAdmin.DEPLOYMENT_HISTORY_TABLE;

/**
 * Runs once per day an removes from the deployment history collection the entries that are 6 or more months old.
 *
 * @author Yannis Theocharis
 */
public class DeploymentHistoryModule implements BatchModule {

	
	
    @Override
	public void start() {
		//make sure the deployment history table exists
    	if(!SystemDB.hasTable(DEPLOYMENT_HISTORY_TABLE)) {
    		SystemDB.createTable(DEPLOYMENT_HISTORY_TABLE);
    	}
	}

	@Override
    public void processBatch(String location, boolean isCloudLocation) throws Throwable {
        // Delete from the deployment history all deployment records that are 6 or more months old.
        final DeployResource[] recordsToDelete = SystemDB.list(DEPLOYMENT_HISTORY_TABLE, DeployResource.class).stream()
                .filter(deployResource -> {
                    final LocalDate deploymentDate = LocalDate.parse(deployResource.getTimestamp().split("T")[0]);
                    return deploymentDate.until(LocalDate.now()).getMonths() >= 6;
                }).toArray(DeployResource[]::new);

        if (recordsToDelete.length > 0) {
            log(INFO, "Deleted " + recordsToDelete.length + "records from the '" + DEPLOYMENT_HISTORY_TABLE + "' table as they are 6 months old.");
            SystemDB.delete(DEPLOYMENT_HISTORY_TABLE, recordsToDelete);
        }
    }

    @Override
    public ModuleLimit getLimits() {
        return ModuleLimit.newInstance()
                .setMaxActiveBatchesPerGSM(1)
                .setBatchFrequency(Frequency.of(TimeUnit.DAYS, 1))
                .build();
    }
}
