<template>

    <v-container grid-list-md>
        <v-layout row wrap>

            <v-card flat class="text-xs-center ma-3">
                <v-card-title primary-title>
                    <div>
                        <h3 class="headline mb-0">
                            Please review values for parameter set '{{paramSet.name}}'
                        </h3>
                    </div>
                </v-card-title>

                <v-card-text>
                    <v-form ref="form" class="mx-4" v-model="valid">

                        <v-text-field v-model="paramSet[params.location.name]"
                                      :label="createLabel(params.location)"
                                      required>
                        </v-text-field>

                        <v-select v-model="paramSet[params.locationType.name]"
                                  :items="params.locationType.range"
                                  :label="createLabel(params.locationType)">
                        </v-select>

                        <v-text-field v-model="paramSet[params.whitelist.name]"
                                      :label="createLabel(params.whitelist)">
                        </v-text-field>

                        <v-text-field v-model="paramSet[params.blacklist.name]"
                                      :label="createLabel(params.blacklist)">
                        </v-text-field>

                        <v-text-field v-model="paramSet[params.polltime.name]"
                                      :label="createLabel(params.polltime)">
                        </v-text-field>

                        <v-switch v-model="paramSet[params.logLocal.name]"
                                  :label="createLabel(params.logLocal)"
                                  color="indigo"
                                  value="true"
                                  hide-details>
                        </v-switch>

                        <v-text-field v-if="paramSet[params.logLocal.name] === 'true'"
                                      v-model="paramSet[params.logOutput.name]"
                                      :label="createLabel(params.logOutput)">
                        </v-text-field>

                        <v-select v-model="paramSet[params.logLevel.name]"
                                  :items="params.logLevel.range"
                                  :label="createLabel(params.logLevel)">
                        </v-select>

                    </v-form>

                </v-card-text>
                <v-card-actions>
                    <v-btn @click="sendParameters" :loading="loading" color="primary">
                        Submit
                    </v-btn>
                </v-card-actions>
            </v-card>

        </v-layout>
    </v-container>
</template>

<script>
    export default {
        data() {
            return {
                csp: this.$route.params.csp,
                paramSet: this.$route.params.paramSet,
                paramSets: this.$route.params.paramSets,
                valid: true,
                loading: false,
                params: {
                    location: {
                        name: 'ECLIPSE_JEMO_LOCATION',
                        description: 'the cloud location name'
                    },
                    locationType: {
                        name: 'ECLIPSE_JEMO_LOCATION_TYPE',
                        description: 'cloud or on-premise',
                        range: ['CLOUD', 'ON-PREMISE'],
                    },
                    whitelist: {
                        name: 'ECLIPSE_JEMO_MODULE_WHITELIST',
                        description: 'list of module ids to allow'
                    },
                    blacklist: {
                        name: 'ECLIPSE_JEMO_MODULE_BLACKLIST',
                        description: 'list of module ids to prevent'
                    },
                    polltime: {
                        name: 'ECLIPSE_JEMO_QUEUE_POLLTIME',
                        description: 'the que poll interval'
                    },
                    logLocal: {
                        name: 'ECLIPSE_JEMO_LOG_LOCAL',
                        description: 'switch to local logging - default is cloud logging'
                    },
                    logOutput: {
                        name: 'ECLIPSE_JEMO_LOG_OUTPUT',
                        description: 'the local log file - default is STDOUT',
                    },
                    logLevel: {
                        name: 'ECLIPSE_JEMO_LOG_LEVEL',
                        description: 'the logging level',
                        range: ['ALL', 'CONFIG', 'FINE', 'FINER', 'FINEST', 'INFO', 'OFF', 'SEVERE', 'WARNING']
                    }
                }

            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'jemo-params') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.paramSet = to.params.paramSet ? to.params.paramSet : this.paramSet;
                    this.paramSets = to.params.paramSets ? to.params.paramSets : this.paramSets;
                    this.loading = false;
                }
            }
        },
        methods: {
            sendParameters: function () {
                this.loading = true;

                let payload = {
                    csp: this.csp.name,
                    parameters: JSON.parse(JSON.stringify(this.paramSet))
                }
                this.$http.post('jemoparams', payload)
                    .then(response => {
                        console.log(response);
                        this.$router.push({
                            name: 'setup-complete',
                            params: {csp: this.csp, paramSet: this.paramSet, paramSets: this.paramSets}
                        })
                    }, response => {
                        console.log(response);
                        this.loading = false;
                        alert(response.data.message);
                    });
            },
            createLabel(param) {
                return param.name + ' (' + param.description + ')';
            }
        }
    }
</script>
