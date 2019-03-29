package org.eclipse.jemo.sys;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author Yannis Theocharis
 */
public class ClusterParams {

    @JsonProperty
    private final List<ClusterParam> master, nodes, network;

    public ClusterParams(List<ClusterParam> master, List<ClusterParam> nodes, List<ClusterParam> network) {
        this.master = master;
        this.nodes = nodes;
        this.network = network;
    }

    public static class ClusterParam {

        @JsonProperty
        private final String name, description, value;

        @JsonProperty
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final List<String> range;

        public ClusterParam(String name, String value, String description) {
            this(name, value, description, null);
        }

        public ClusterParam(String name, String value, String description, List<String> range) {
            this.name = name;
            this.description = description;
            this.value = value;
            this.range = range;
        }
    }
}
