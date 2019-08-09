package org.eclipse.jemo.sys;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Models an accumulator of values used for reporting the execution times of the running modules.
 *
 * @author Yannis Theocharis
 */
public class Accumulator {

    @JsonProperty
    private AtomicLong samples = new AtomicLong();

    @JsonProperty
    private AtomicLong totalTime = new AtomicLong();

    public synchronized Accumulator add(long execTime) {
        samples.incrementAndGet();
        totalTime.addAndGet(execTime);
        return this;
    }

    public double avg() {
        return ((double) totalTime.get()) / samples.get();
    }

    @Override
    public String toString() {
        return "Accumulator{" +
                "samples=" + samples +
                ", totalTime=" + totalTime +
                '}';
    }

}
