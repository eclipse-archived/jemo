package org.eclipse.jemo.tutorial.market;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * @author Yannis Theocharis
 */
public class Transaction {

    @JsonProperty
    private LocalDateTime time;

    @JsonProperty
    private String buyer;

    @JsonProperty
    private String seller;

    @JsonProperty
    private String stock;

    @JsonProperty
    private Float value;

    public Transaction() {
    }

    public Transaction(LocalDateTime time, String buyer, String seller, String stock, Float value) {
        this.time = time;
        this.buyer = buyer;
        this.seller = seller;
        this.stock = stock;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "time=" + time +
                ", buyer='" + buyer + '\'' +
                ", seller='" + seller + '\'' +
                ", stock='" + stock + '\'' +
                ", value=" + value +
                '}';
    }
}
