package org.eclipse.jemo.tutorial.market;

import org.eclipse.jemo.internal.model.CloudRuntime;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Models a stock NoSQL repository.
 * Delegates method calls to the CloudRuntime implementation.
 *
 * @author Yannis Theocharis
 */
public class StockRepository {

    private static final String TABLE_NAME = "stocks";

    private final CloudRuntime runtime;

    public StockRepository(CloudRuntime runtime) {
        this.runtime = runtime;
    }

    public void init() {
        if (!runtime.hasNoSQLTable(TABLE_NAME)) {
            runtime.createNoSQLTable(TABLE_NAME);
        }
    }

    public List<Stock> findAll() {
        return runtime.listNoSQL(TABLE_NAME, Stock.class);
    }

    public Optional<Stock> findById(String id) {
        try {
            return Optional.ofNullable(runtime.getNoSQL(TABLE_NAME, id, Stock.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save(Stock... stocks) {
        runtime.saveNoSQL(TABLE_NAME, stocks);
    }

    public OptionalInt findMaxId() {
        if (runtime.hasNoSQLTable(TABLE_NAME)) {
            return findAll().stream().mapToInt(stock -> Integer.parseInt(stock.getId())).max();
        } else {
            return OptionalInt.empty();
        }
    }
}
