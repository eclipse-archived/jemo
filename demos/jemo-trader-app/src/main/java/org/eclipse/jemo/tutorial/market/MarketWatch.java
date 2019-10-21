package org.eclipse.jemo.tutorial.market;

import org.eclipse.jemo.api.FixedModule;
import org.eclipse.jemo.api.ModuleLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.logging.Level.INFO;

/**
 * Declares a fixed process that logs the transactions processed by this instance the last 30 seconds.
 *
 * @author Yannis Theocharis
 */
public class MarketWatch implements FixedModule {

    public static final List<Transaction> TRANSACTIONS = new ArrayList<>();
    public AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public void processFixed(String location, String instanceId) throws Throwable {
        log(INFO, String.format("Process fixed Location [%s] - instance [%s]: [%s]", location, instanceId, TRANSACTIONS));
        while (running.get()) {
            // We could send the transactions to a consumer. For demo purposes we just log them.
            TRANSACTIONS.forEach(txn -> log(INFO, String.format("Txn processed in Location [%s] - instance [%s]: [%s]", location, instanceId, txn)));
            TRANSACTIONS.clear();
            TimeUnit.SECONDS.sleep(30);
        }
    }

    @Override
    public ModuleLimit getLimits() {
        return ModuleLimit.newInstance()
                .setMaxActiveFixedPerInstance(1)
                .build();
    }
}
