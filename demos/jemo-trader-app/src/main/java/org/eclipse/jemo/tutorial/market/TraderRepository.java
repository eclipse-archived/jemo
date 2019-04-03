package org.eclipse.jemo.tutorial.market;

import org.eclipse.jemo.internal.model.CloudRuntime;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Models a trader NoSQL repository.
 * Delegates method calls to the CloudRuntime implementation.
 *
 * @author Yannis Theocharis
 */
public class TraderRepository {

    private static final String TABLE_NAME = "traders";

    private final CloudRuntime runtime;

    public TraderRepository(CloudRuntime runtime) {
        this.runtime = runtime;
    }

    public boolean init() {
        if (!runtime.hasNoSQLTable(TABLE_NAME)) {
            runtime.createNoSQLTable(TABLE_NAME);
            return true;
        } else {
            return false;
        }
    }

    public List<Trader> findAll() {
        return runtime.listNoSQL(TABLE_NAME, Trader.class);
    }

    public Optional<Trader> findById(String id) throws IOException {
        return Optional.ofNullable(runtime.getNoSQL(TABLE_NAME, id, Trader.class));
    }

    public void save(Trader... trader) {
        runtime.saveNoSQL(TABLE_NAME, trader);
    }

    public void deleteAll() {
        runtime.deleteNoSQL(TABLE_NAME, runtime.listNoSQL(TABLE_NAME, Trader.class).toArray(new Trader[0]));
    }
}
