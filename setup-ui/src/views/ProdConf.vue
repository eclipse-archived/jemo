<template>

    <v-container v-if="params" grid-list-md>
        <v-layout row wrap>

            <v-card class="text-md-center ma-3">
                <v-card-title primary-title>
                    <div>
                        <h3 class="headline mb-0">
                            Please review cluster parameter values
                        </h3>
                    </div>
                </v-card-title>

                <v-divider></v-divider>
                <v-subheader>Cluster master parameters</v-subheader>
                <v-card-text>
                    <div v-for="param in params.master" :key="param.name">
                        <v-select v-if="param.range"
                                  v-model="param.value"
                                  :items="param.range"
                                  :label="createLabel(param)">
                        </v-select>

                        <v-text-field v-else
                                      v-model="param.value"
                                      :label="createLabel(param)"
                                      required>
                        </v-text-field>
                    </div>
                </v-card-text>

                <v-divider></v-divider>
                <v-subheader>Cluster nodes parameters</v-subheader>
                <v-card-text>
                    <div v-for="param in params.nodes" :key="param.name">
                        <v-select v-if="param.range"
                                  v-model="param.value"
                                  :items="param.range"
                                  :label="createLabel(param)">
                        </v-select>

                        <v-text-field v-else
                                      v-model="param.value"
                                      :label="createLabel(param)"
                                      required>
                        </v-text-field>
                    </div>
                </v-card-text>

                <div v-if="policies">
                    <v-divider></v-divider>
                    <v-subheader>Policy to attach to cluster and worker nodes</v-subheader>
                    <v-card-text>
                        <v-select v-model="selectedPolicy"
                                  :items="policies"
                                  label="Existing Policies"
                                  v-on:input="changeRoute()">
                        </v-select>
                    </v-card-text>
                </div>


                <div v-if="params.network.length > 0">
                    <v-divider></v-divider>
                    <v-subheader>Network parameters</v-subheader>
                    <v-card-text v-if="!existingNetworks">
                        <div v-for="param in params.network" :key="param.name">
                            <v-text-field
                                    v-model="param.value"
                                    :label="createLabel(param)"
                                    required>
                            </v-text-field>
                        </div>
                        <v-btn @click="getExistingNetworks" color="primary">Or Select existing network</v-btn>
                    </v-card-text>
                    <v-card-text v-else>
                        <v-select v-model="selectedNetwork"
                                  :items="existingNetworks"
                                  label="Existing Networks">
                        </v-select>
                        <v-btn @click="existingNetworks=null" color="primary">Or Select new network</v-btn>
                    </v-card-text>
                </div>

                <v-divider></v-divider>
                <v-subheader>Map containers to parameter sets</v-subheader>
                <v-card-text>
                    <v-text-field v-for="(value, key) in instanceParamSets"
                                  v-model="instanceParamSets[key]"
                                  :label="createReplicasLabel(key)"
                                  :key="key"
                                  required>
                    </v-text-field>
                </v-card-text>

                <v-card-actions>
                    <v-layout row justify-center>
                        <v-dialog v-model="permissions_error_dialog" persistent max-width="600px" class="mx-1">
                            <v-btn slot="activator" color="primary" dark>Create Cluster</v-btn>
                            <v-card>
                                <v-card-title>
                                    <span class="headline">Terraform user credentials</span>
                                </v-card-title>
                                <v-card-text>
                                    <v-container grid-list-md>
                                        <v-layout wrap>

                                            <v-flex xs12 v-for="credential in csp.requiredCredentials"
                                                    :key="credential">
                                                <v-text-field v-model="credentialParameters[credential]"
                                                              :label="credential"></v-text-field>
                                            </v-flex>
                                        </v-layout>
                                    </v-container>
                                </v-card-text>
                                <v-card-actions>
                                    <v-spacer></v-spacer>
                                    <v-btn color="blue darken-1" flat @click="permissions_error_dialog = false">Cancel</v-btn>
                                    <v-btn color="blue darken-1" flat @click="validateCredentials">OK</v-btn>
                                </v-card-actions>

                            </v-card>
                        </v-dialog>
                        <v-btn @click="deployCluster(true)" color="secondary">Download Terraform Templates</v-btn>
                    </v-layout>
                </v-card-actions>
            </v-card>

            <div v-if="clusterCreated">
                <h3>Cluster created</h3>
                Great job! The cluster is created. Terraform has created the following resources:
                <br/>

                <div>
                    <pre>{{ terraformResult | pretty }}</pre>
                </div>
            </div>
        </v-layout>

        <v-dialog v-model="policyValidationErrorDialog" persistent max-width="600">
            <v-card>
                <v-card-title class="headline">Policy '{{selectedPolicy}}' does not have the required permissions
                </v-card-title>
                <v-card-text>Please select another policy or add to '{{selectedPolicy}}' the following permissions:
                </v-card-text>
                <v-list dense>
                    <v-list-tile
                            v-for="permission in policyValidationError"
                            :key="permission">

                        <v-list-tile-content>
                            <v-list-tile-title v-text="permission"></v-list-tile-title>
                        </v-list-tile-content>

                    </v-list-tile>
                </v-list>
                <v-card-actions>
                    <v-spacer></v-spacer>
                    <v-btn color="green darken-1" flat @click="policyValidationErrorDialog = false">Close</v-btn>
                </v-card-actions>
            </v-card>
        </v-dialog>

    </v-container>

</template>

<script>
    export default {
        data() {
            return {
                csp: this.$route.params.csp,
                paramSets: this.$route.params.paramSets,
                valid: true,
                clusterCreated: false,
                terraformResult: null,
                permissions_error_dialog: false,
                existingNetworks: null,
                selectedNetwork: null,
                policies: null,
                selectedPolicy: null,
                policyValidationErrorDialog: false,
                policyValidationError: [],
                credentialParameters: {},
                params: null,
                instanceParamSets: {}
            }
        },
        methods: {
            createLabel(param) {
                return param.name + ' (' + param.description + ')';
            },
            createReplicasLabel(paramSetName) {
                return 'Number of containers using ' + paramSetName;
            },
            getExistingNetworks() {
                this.$http.get('networks/' + this.csp.name)
                    .then(response => {
                        console.log(response);
                        this.existingNetworks = response.data;
                    }, response => {
                        console.log(response);
                        alert(response.data);
                    });
            },
            validateCredentials() {
                this.credentialParameters['region'] = this.csp.region;
                const payload = {
                    csp: this.csp.name,
                    parameters: this.credentialParameters
                };
                console.log(payload);
                this.$http.post('credentials', payload)
                    .then(response => {
                        console.log(response);
                        this.permissions_error_dialog = false;
                        this.deployCluster(false);
                    }, response => {
                        console.log(response);
                        alert(response.data);
                    });
            },
            changeRoute() {
                console.log(this.selectedPolicy);
                const payload = {
                    csp: this.csp.name,
                    parameters: {
                        policy: this.selectedPolicy
                    }
                };
                this.$http.post('policy/validate', payload)
                    .then(response => {
                        console.log(response.data);
                        if (response.data.notAllowedActions.length > 0) {
                            this.policyValidationErrorDialog = true;
                            this.policyValidationError = response.data.notAllowedActions;
                        }
                    }, response => {
                        alert(response.data);
                    });
            },
            deployCluster(downloadFormsOnly) {
                const parameters = {};
                for (let key in this.params.master) {
                    let param = this.params.master[key];
                    parameters[param.name] = param.value;
                }

                for (let key in this.params.nodes) {
                    let param = this.params.nodes[key];
                    parameters[param.name] = param.value;
                }

                if (this.selectedNetwork) {
                    parameters['existing-network-name'] = this.selectedNetwork;
                } else {
                    for (let key in this.params.network) {
                        let param = this.params.network[key];
                        parameters[param.name] = param.value;
                    }
                }

                let containersPerParamSet = Object.keys(this.instanceParamSets)
                    .map(k => k + ':' + this.instanceParamSets[k])
                    .join(",");
                parameters['containersPerParamSet'] = containersPerParamSet;

                if (this.selectedPolicy) {
                    parameters['jemo-policy-name'] = this.selectedPolicy;
                }

                this.$router.push({
                    name: 'create-cluster',
                    params: {csp: this.csp, parameters: parameters, downloadFormsOnly: downloadFormsOnly}
                })
            },
            init() {
                this.$http.get('cluster/params/' + this.csp.name)
                    .then(response => {
                        console.log(response);
                        this.params = response.data;

                        let workstationExternalCidr = this.params.master.find(param => param.name === 'workstation-external-cidr');
                        if (workstationExternalCidr) {
                            this.$http.get('https://ipv4.icanhazip.com')
                                .then(response => {
                                    console.log(response);
                                    workstationExternalCidr.value = response.data.trim() + '/32';
                                }, response => {
                                    console.log("Can not find local ip");
                                    console.log(response);
                                });
                        }
                    }, response => {
                        console.log(response);
                    });

                this.$http.get('policy/' + this.csp.name)
                    .then(response => {
                        console.log(response);
                        this.policies = response.data;
                        if (this.policies && this.policies.includes('jemo-policy')) {
                            this.selectedPolicy = 'jemo-policy';
                        }
                    }, response => {
                        console.log(response);
                    });

                this.paramSets.forEach(paramSet => this.instanceParamSets[paramSet.name] = 0);
            }
        },
        created() {
            this.init();
        },
        watch: {
            '$route'(to) {
                if (to.name === 'prod-conf') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.paramSets = to.params.paramSets ? to.params.paramSets : this.paramSets;

                    if (to.params.csp.name !== this.csp.name) {
                        this.init();
                    } else {
                        this.paramSets.forEach(paramSet => this.instanceParamSets[paramSet.name] = 0);
                    }
                }
            }
        },
    }
</script>
