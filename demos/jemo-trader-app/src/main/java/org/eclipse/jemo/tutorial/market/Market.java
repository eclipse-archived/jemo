package org.eclipse.jemo.tutorial.market;

import org.eclipse.jemo.api.WebServiceModule;
import org.eclipse.jemo.internal.model.JemoMessage;
import org.eclipse.jemo.sys.internal.Util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.logging.Level.INFO;
import static org.eclipse.jemo.tutorial.market.MarketMatcher.TRADER_ID;

/**
 * Demonstration of the Web service Jemo development pattern.
 * Implements a REST API for modeling a market with traders and stocks.
 *
 * @author Yannis Theocharis
 */
public class Market implements WebServiceModule {

    private static final Pattern TRADERS_PATTERN = Pattern.compile("/market/traders");
    private static final Pattern ONE_TRADER_PATTERN = Pattern.compile("/market/traders/(\\d+)");
    private static final Pattern STOCKS_PATTERN = Pattern.compile("/market/stocks");
    private static final Pattern ONE_STOCK_PATTERN = Pattern.compile("/market/stocks/(\\d+)");
    public static int CURRENT_STOCK_ID = 21;

    public static TraderRepository TRADER_REPOSITORY;
    public static StockRepository STOCK_REPOSITORY;

    @Override
    public void construct(Logger logger, String name, int id, double version) {
        WebServiceModule.super.construct(logger, name, id, version);
        TRADER_REPOSITORY = new TraderRepository(getRuntime());
        STOCK_REPOSITORY = new StockRepository(getRuntime());
        STOCK_REPOSITORY.findMaxId().ifPresent(maxId -> CURRENT_STOCK_ID = maxId + 1);
    }

    @Override
    public void installed() {
        log(INFO, "Installed phase. Initializing the database...");
        final boolean wasInitNeeded = TRADER_REPOSITORY.init();
        STOCK_REPOSITORY.init();

        if (!wasInitNeeded) {
            log(INFO, "The database is already initialized...");
            return;
        }

        // Create 20 stocks.
        final Stock[] stocks = IntStream.range(1, 21)
                .mapToObj(id -> new Stock(String.valueOf(id), 100f))
                .toArray(Stock[]::new);
        STOCK_REPOSITORY.save(stocks);

        // Create 10 traders and assign 2 stock to each one of them.
        final Trader[] traders = IntStream.range(1, 11)
                .mapToObj(id -> {
                    final int stockIndex = 2 * (id - 1);
                    final Trader trader = new Trader(String.valueOf(id), 1000f).acquire(stocks[stockIndex]);
                    if (stockIndex + 1 < CURRENT_STOCK_ID) {
                        trader.acquire(stocks[stockIndex + 1]);
                    }
                    return trader;
                })
                .toArray(Trader[]::new);
        TRADER_REPOSITORY.save(traders);
    }

    @Override
    public String getBasePath() {
        return "/market";
    }

    @Override
    public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        final String endpoint = request.getRequestURI().substring(request.getRequestURI().indexOf(getBasePath()));
        switch (request.getMethod()) {
            case "GET":
                Matcher matcher;
                log(INFO, "endpoint: " + endpoint);
                if ((matcher = ONE_TRADER_PATTERN.matcher(endpoint)).find()) {
                    getTrader(matcher.group(1), response);
                } else if (TRADERS_PATTERN.matcher(endpoint).find()) {
                    getAllTraders(response);
                } else if ((matcher = ONE_STOCK_PATTERN.matcher(endpoint)).find()) {
                    getStock(matcher.group(1), response);
                } else if (STOCKS_PATTERN.matcher(endpoint).find()) {
                    getAllStocks(response);
                } else {
                    response.sendError(400);
                }
                break;

            case "PUT":
                if ((matcher = ONE_TRADER_PATTERN.matcher(endpoint)).find()) {
                    updateTrader(matcher.group(1), request, response);
                } else {
                    response.sendError(400);
                }
                break;

            case "PATCH":
                if ((matcher = ONE_TRADER_PATTERN.matcher(endpoint)).find()) {
                    partialUpdateTrader(matcher.group(1), request, response);
                } else {
                    response.sendError(400);
                }
                break;
            default:
                response.sendError(400);
        }
    }

    private void getAllTraders(HttpServletResponse response) throws IOException {
        respondWithJson(200, response, TRADER_REPOSITORY.findAll());
    }

    private void getTrader(String id, HttpServletResponse response) throws IOException {
        final Optional<Trader> trader = TRADER_REPOSITORY.findById(id);
        if (trader.isPresent()) {
            respondWithJson(200, response, trader.get());
        } else {
            respondWithJson(404, response, null);
        }
    }

    private void updateTrader(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log(INFO, "updateTrader");
        final Optional<Trader> trader = TRADER_REPOSITORY.findById(id);
        if (trader.isPresent()) {
            final Trader newState = Util.fromJSONString(Trader.class, Util.toString(request.getInputStream()));
            final boolean differsInTargetValue = newState.differsInTargetValue(trader.get());
            TRADER_REPOSITORY.save(newState);
            if (differsInTargetValue) {
                triggerEvent(newState.getId());
            }
            respondWithJson(200, response, newState);
        } else {
            respondWithJson(404, response, null);
        }
    }

    private void partialUpdateTrader(String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log(INFO, "partialUpdateTrader");
        final Optional<Trader> optionalTrader = TRADER_REPOSITORY.findById(id);
        if (optionalTrader.isPresent()) {
            final Trader trader = optionalTrader.get();
            final Trader newState = Util.fromJSONString(Trader.class, Util.toString(request.getInputStream()));
            final boolean differsInTargetValue = newState.differsInTargetValue(trader);
            trader.setStockIdToBuyTargetValue(newState.getStockIdToBuyTargetValue());
            trader.setStockIdToSellTargetValue(newState.getStockIdToSellTargetValue());
            TRADER_REPOSITORY.save(trader);
            if (differsInTargetValue) {
                triggerEvent(trader.getId());
            }
            respondWithJson(200, response, trader);
        } else {
            respondWithJson(404, response, null);
        }
    }

    private void triggerEvent(String traderId) {
        JemoMessage msg = new JemoMessage();
        msg.setModuleClass(MarketMatcher.class.getName());
        msg.setId("1");
        msg.setPluginId(1);
        msg.setPluginVersion(1.0); // Needs to be the same as the pom version
        msg.getAttributes().put(TRADER_ID, traderId);
        msg.send(JemoMessage.LOCATION_LOCALLY);
    }

    private void getAllStocks(HttpServletResponse response) throws IOException {
        respondWithJson(200, response, STOCK_REPOSITORY.findAll());
    }

    private void getStock(String id, HttpServletResponse response) throws IOException {
        final Optional<Stock> stock = STOCK_REPOSITORY.findById(id);
        if (stock.isPresent()) {
            respondWithJson(200, response, stock.get());
        } else {
            respondWithJson(404, response, null);
        }
    }

    private static void respondWithJson(int statusCode, HttpServletResponse response, Object obj) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        final OutputStream out = response.getOutputStream();
        final String json = Util.toJSONString(obj);
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.flush();
        out.close();
    }

}
