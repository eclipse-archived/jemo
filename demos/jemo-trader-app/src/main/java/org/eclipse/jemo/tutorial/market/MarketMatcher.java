package org.eclipse.jemo.tutorial.market;

import org.eclipse.jemo.api.EventModule;
import org.eclipse.jemo.internal.model.JemoMessage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.logging.Level.INFO;
import static org.eclipse.jemo.tutorial.market.Market.STOCK_REPOSITORY;
import static org.eclipse.jemo.tutorial.market.Market.TRADER_REPOSITORY;
import static org.eclipse.jemo.tutorial.market.MarketWatch.TRANSACTIONS;

/**
 * Consumes Jemo Messages produced when a trader changes his target values.
 * Attempts to match these target values with other traders target values.
 *
 * @author Yannis Theocharis
 */
public class MarketMatcher implements EventModule {

    public static final String TRADER_ID = "TRADER_ID";

    @Override
    public JemoMessage process(JemoMessage msg) throws IOException {
        log(INFO, "Consuming message...");

        final Trader sourceTrader = TRADER_REPOSITORY.findById((String) msg.getAttributes().get(TRADER_ID)).get();

        final List<Trader> traders = TRADER_REPOSITORY.findAll();
        Collections.shuffle(traders);

        for (Map.Entry<String, Float> entry : sourceTrader.getStockIdToBuyTargetValue().entrySet()) {
            final Optional<Trader> targetTrader = traders.stream()
                    .filter(trader -> trader != sourceTrader && (trader.sellTargetValue(entry.getKey()) != null && trader.sellTargetValue(entry.getKey()) <= entry.getValue()))
                    .findFirst();
            if (targetTrader.isPresent()) {
                final Trader seller = targetTrader.get();
                final Float value = seller.sellTargetValue(entry.getKey());
                trade(sourceTrader, seller, entry.getKey(), value);
                break;
            }
        }

        for (Map.Entry<String, Float> entry : sourceTrader.getStockIdToSellTargetValue().entrySet()) {
            final Optional<Trader> targetTrader = traders.stream()
                    .filter(trader -> trader != sourceTrader && trader.buyTargetValue(entry.getKey()) != null && trader.buyTargetValue(entry.getKey()) >= entry.getValue())
                    .findFirst();
            if (targetTrader.isPresent()) {
                final Trader buyer = targetTrader.get();
                final Float value = buyer.buyTargetValue(entry.getKey());
                trade(buyer, sourceTrader, entry.getKey(), value);
                break;
            }
        }

        return null;
    }

    private void trade(Trader buyer, Trader seller, String stockId, Float value) {
        log(INFO, String.format("Matching buyer [%s] with seller [%s] for stock [%s] and value [%s]...", buyer.getId(), seller.getId(), stockId, value));

        final Stock stock = STOCK_REPOSITORY.findById(stockId).get();
        buyer.buy(stock, value);
        seller.sell(stock, value);
        stock.setValue(value);
        TRADER_REPOSITORY.save(buyer, seller);
        STOCK_REPOSITORY.save(stock);
        TRANSACTIONS.add(new Transaction(LocalDateTime.now(), buyer.getId(), seller.getId(), stockId, value));
    }

}
