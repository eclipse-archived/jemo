package org.eclipse.jemo.tutorial.market;

import org.eclipse.jemo.api.BatchModule;
import org.eclipse.jemo.api.Frequency;
import org.eclipse.jemo.api.ModuleLimit;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.util.logging.Level.INFO;
import static org.eclipse.jemo.tutorial.market.Market.*;

/**
 * Simulates a simplistic IPO, where 1 new stock is added to the market and owned by an existing trader.
 * This is repeated every 1 minute that the processBatch is called by Jemo.
 *
 * @author Yannis Theocharis
 */
public class MarketIPO implements BatchModule {
    private static Random RANDOM = new Random();

    @Override
    public void processBatch(String location, boolean isCloudLocation) throws Throwable {
        final Trader trader = TRADER_REPOSITORY.findById(String.valueOf(RANDOM.nextInt(10) + 1)).get();
        final Stock stock = new Stock(String.valueOf(CURRENT_STOCK_ID++), 100f);
        log(INFO, String.format("An IPO occurred - Trader [%s] owns the stock [%s]", trader.getId(), stock.getId()));
        trader.acquire(stock);
        TRADER_REPOSITORY.save(trader);
        STOCK_REPOSITORY.save(stock);
    }

    @Override
    public ModuleLimit getLimits() {
        return ModuleLimit.newInstance()
                .setMaxActiveBatchesPerGSM(1)
                .setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 30))
                .build();
    }
}
