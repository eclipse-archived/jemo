package org.eclipse.jemo.sys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Yannis Theocharis
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class", include = JsonTypeInfo.As.PROPERTY)
public class ModuleInfo implements Serializable {
    private int id = 0;
    private double version = 0;
    private String name = null;
    private String implementation = null;
    private boolean batch = false;

    public ModuleInfo() {
    }

    public ModuleInfo(int id, double version, String name, String implementation, boolean batch) {
        this.id = id;
        this.version = version;
        this.name = name;
        this.implementation = implementation;
        this.batch = batch;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getVersion() {
        return version;
    }

    public void setVersion(double version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }

    public boolean isBatch() {
        return batch;
    }

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    @JsonIgnore
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + this.id;
        hash = 71 * hash + (int) (Double.doubleToLongBits(this.version) ^ (Double.doubleToLongBits(this.version) >>> 32));
        hash = 71 * hash + Objects.hashCode(this.implementation);
        return hash;
    }

    @JsonIgnore
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ModuleInfo other = (ModuleInfo) obj;
        if (this.id != other.id) {
            return false;
        }
        if (Double.doubleToLongBits(this.version) != Double.doubleToLongBits(other.version)) {
            return false;
        }
        if (!Objects.equals(this.implementation, other.implementation)) {
            return false;
        }
        return true;
    }
}
