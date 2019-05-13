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
                        'eclipse.jemo.location': this.csp.name === 'MEMORY' ? 'JEMO' : this.csp.name,
                        'eclipse.jemo.module.whitelist': '',
                        'eclipse.jemo.module.blacklist': '',
                        'eclipse.jemo.queue.polltime': '20000',
                        'eclipse.jemo.location.type': 'ON-PREMISE',
                        'eclipse.jemo.log.local': 'false',
                        'eclipse.jemo.log.level': 'INFO',
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
