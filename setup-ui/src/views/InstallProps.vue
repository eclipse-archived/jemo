<template>

    <v-container grid-list-md>
        <v-layout row wrap>

            <v-flex xs12 sm10 md9 lg7>
                <v-card flat class="text-xs-center ma-3" v-if="!hasFinished">
                    <v-card-title primary-title>
                        <div>
                            <h3 class="headline mb-0">
                                Please enter the names of the created resources
                            </h3>
                        </div>
                    </v-card-title>

                    <v-card-text>
                        <v-form ref="form" class="mx-4">

                            <div v-for="prop in csp.installProperties" :key="prop.name">
                                <div v-if="prop.range">
                                    <v-select v-model="prop.value"
                                              :items="prop.range"
                                              :label="createLabel(prop)">
                                    </v-select>
                                </div>
                                <div v-else>
                                    <v-text-field v-model="prop.value"
                                                  :label="createLabel(prop)">
                                    </v-text-field>
                                </div>
                            </div>

                        </v-form>

                    </v-card-text>
                    <v-card-actions>
                        <v-btn @click="sendInstallProperties" :loading="loading" color="primary">
                            Submit
                        </v-btn>
                    </v-card-actions>
                </v-card>
            </v-flex>

        </v-layout>
    </v-container>
</template>

<script>
    export default {
        data() {
            return {
                csp: this.$route.params.csp,
                hasFinished: false,
                loading: false
            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'install-props') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.hasFinished = false;
                    this.loading = false;
                }
            }
        },
        methods: {
            sendInstallProperties: function () {
                this.loading = true;

                const parameters = {};
                this.csp.installProperties.forEach(prop => parameters[prop.name] = prop.value);

                let payload = {
                    csp: this.csp.name,
                    parameters: parameters
                }
                this.$http.post('install/props', payload)
                    .then(response => {
                        console.log(response);
                        this.hasFinished = true;
                        this.$router.push({name: 'csp', params: {csp: this.csp}})
                    }, response => {
                        console.log(response);
                        alert(response.data.message);
                    });
            },
            createLabel(param) {
                return param.name + ' (' + param.description + ')';
            }
        }
    }
</script>
