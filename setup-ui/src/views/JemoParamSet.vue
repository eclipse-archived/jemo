<template>

    <v-container grid-list-md>
        <v-layout row wrap>


            <v-container fluid>
                <v-layout row wrap align-center>
                    <v-flex xs6>
                        <v-subheader>Select Existing Parameter Set</v-subheader>
                    </v-flex>

                    <v-flex xs6>
                        <v-select
                                v-model="existingParamSet"
                                :items="paramSets"
                                item-text="name"
                                item-value="name"
                                label="param_set_name"
                                return-object
                                single-line>
                        </v-select>
                    </v-flex>

                </v-layout>
                <v-layout row wrap align-center>
                    <v-flex xs6>
                        <v-subheader>Or Create a new Parameter Set</v-subheader>
                    </v-flex>

                    <v-flex xs6>
                        <v-form>
                            <v-text-field
                                    v-model="newParamSet"
                                    required>
                            </v-text-field>
                        </v-form>
                    </v-flex>
                </v-layout>

                <v-btn class="mx-4" @click="changeParams" color="primary">
                    Submit
                </v-btn>

            </v-container>
        </v-layout>
    </v-container>
</template>

<script>
    export default {
        data() {
            return {
                csp: this.$route.params.csp,
                paramSets: this.$route.params.paramSets,
                existingParamSet: null,
                newParamSet: null
            }
        },
        mounted() {
            if (this.paramSets && this.paramSets.length > 0) {
                this.existingParamSet = this.paramSets[0];
            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'jemo-param-set') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.paramSets = to.params.paramSets ? to.params.paramSets : this.paramSets;
                }
            }
        },
        methods: {
            changeParams: function () {
                let paramSet = null;
                if (this.newParamSet) {
                    paramSet = {
                        'ECLIPSE_JEMO_LOCATION': this.csp.name === 'MEMORY' ? 'JEMO' : this.csp.name,
                        'ECLIPSE_JEMO_MODULE_WHITELIST': '',
                        'ECLIPSE_JEMO_MODULE_BLACKLIST': '',
                        'ECLIPSE_JEMO_QUEUE_POLLTIME': '20000',
                        'ECLIPSE_JEMO_LOCATION_TYPE': 'ON-PREMISE',
                        'ECLIPSE_JEMO_LOG_LOCAL': 'false',
                        'ECLIPSE_JEMO_LOG_LEVEL': 'INFO',
                        'name': this.newParamSet
                    };
                    this.paramSets.push(paramSet);
                } else {
                    paramSet = this.existingParamSet;
                }
                this.$router.push({
                    name: 'jemo-params', params: {csp: this.csp, paramSet: paramSet, paramSets: this.paramSets}
                })
            }
        }
    }
</script>
