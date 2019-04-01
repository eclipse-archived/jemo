package org.eclipse.jemo.tutorial.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.jemo.internal.model.SystemDBObject;

import java.util.*;

/**
 * Models a trader.
 * Traders can have stocks and can set buy and sell target values for stocks.
 *
 * @author Yannis Theocharis
 */
public class Trader implements SystemDBObject {

    @JsonProperty
    private String id;

    @JsonProperty
    private float accountBalance;

    @JsonProperty
    private Set<Stock> stocks;

    @JsonProperty
    private Map<String, Float> stockIdToBuyTargetValue;

    @JsonProperty
    private Map<String, Float> stockIdToSellTargetValue;

    public Trader() {
    }

    public Trader(String id, float accountBalance) {
        this.id = id;
        this.accountBalance = accountBalance;
        stocks = new HashSet<>();
        stockIdToBuyTargetValue = new HashMap<>();
        stockIdToSellTargetValue = new HashMap<>();
    }

    @Override
    public String getId() {
        return id;
    }

    public Trader acquire(Stock stock) {
        stocks.add(stock);
        return this;
    }

    public boolean buy(Stock stock, float value) {
        if (accountBalance >= value) {
            accountBalance -= value;
            stocks.add(stock);
            stockIdToBuyTargetValue.remove(stock.getId());
            return true;
        }
        return false;
    }

    public boolean sell(Stock stock, float value) {
        if (stocks.contains(stock)) {
            accountBalance += value;
            stocks.remove(stock);
            stockIdToSellTargetValue.remove(stock.getId());
            return true;
        }
        return false;
    }

    public boolean differsInTargetValue(Trader other) {
        return !this.stockIdToBuyTargetValue.equals(other.stockIdToBuyTargetValue) ||
                !this.stockIdToSellTargetValue.equals(other.stockIdToSellTargetValue);
    }

    public Map<String, Float> getStockIdToBuyTargetValue() {
        return stockIdToBuyTargetValue;
    }

    public void setStockIdToBuyTargetValue(Map<String, Float> stockIdToBuyTargetValue) {
        this.stockIdToBuyTargetValue = stockIdToBuyTargetValue;
    }

    public Map<String, Float> getStockIdToSellTargetValue() {
        return stockIdToSellTargetValue;
    }

    public void setStockIdToSellTargetValue(Map<String, Float> stockIdToSellTargetValue) {
        this.stockIdToSellTargetValue = stockIdToSellTargetValue;
    }

    public Float buyTargetValue(String stockId) {
        return stockIdToBuyTargetValue.get(stockId);
    }

    public Float sellTargetValue(String stockId) {
        return stockIdToSellTargetValue.get(stockId);
    }

    @Override
    public String toString() {
        return "Trader{" +
                "id='" + id + '\'' +
                ", accountBalance=" + accountBalance +
                ", stocks=" + stocks +
                ", stockIdToBuyTargetValue=" + stockIdToBuyTargetValue +
                ", stockIdToSellTargetValue=" + stockIdToSellTargetValue +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trader trader = (Trader) o;
        return Float.compare(trader.accountBalance, accountBalance) == 0 &&
                Objects.equals(id, trader.id) &&
                Objects.equals(stocks, trader.stocks) &&
                Objects.equals(stockIdToBuyTargetValue, trader.stockIdToBuyTargetValue) &&
                Objects.equals(stockIdToSellTargetValue, trader.stockIdToSellTargetValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, accountBalance, stocks, stockIdToBuyTargetValue, stockIdToSellTargetValue);
    }
}
