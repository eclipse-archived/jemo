package org.eclipse.jemo.tutorial.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.jemo.internal.model.SystemDBObject;

import java.util.Objects;

/**
 * Models a stock.
 *
 * @author Yannis Theocharis
 */
public class Stock implements SystemDBObject {

    @JsonProperty
    private String id;

    @JsonProperty
    private float value;

    public Stock() {
    }

    public Stock(String id, float value) {
        this.id = id;
        this.value = value;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setValue(float value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stock stock = (Stock) o;
        return Objects.equals(id, stock.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Stock{" +
                "id='" + id + '\'' +
                ", value=" + value +
                '}';
    }
}
