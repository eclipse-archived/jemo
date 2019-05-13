<template>

    <v-container grid-list-md>
        <v-layout row wrap>
            <v-card flat class="text-xs-left ma-3">
                <v-card-title primary-title>
                    <div>
                        <h3 class="headline mb-0">
                            Setup Complete
                        </h3>
                    </div>
                </v-card-title>
                <v-card-text>
                    You can configure a production environment, or alternatively create another parameter set.
                    Otherwise, you can initialize a local Jemo instance with the parameter set '{{paramSet.name}}'.
                </v-card-text>
                <v-card-actions>
                    <v-btn v-if="csp.name !== 'MEMORY'" @click="configureProdEnv" color="primary">
                        Configure a production environment
                    </v-btn>
                    <v-btn :to="{name: 'jemo-param-set', params: {csp: this.csp}}" color="secondary">
                        Create another parameter set
                    </v-btn>
                    <v-btn @click="startLocalJemoInstace" :loading="loading" color="green" dark>
                        Start local Jemo instance
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
                loading: false
            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'setup-complete') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.paramSet = to.params.paramSet ? to.params.paramSet : this.paramSet;
                    this.paramSets = to.params.paramSets ? to.params.paramSets : this.paramSets;
                    this.loading = false;
                }
            }
        },
        methods: {
            configureProdEnv() {
                this.$router.push({
                    name: 'prod-conf',
                    params: {csp: this.csp, paramSets: this.paramSets}
                })
            },
            startLocalJemoInstace() {
                this.loading = true;
                let payload = {
                    csp: this.csp.name,
                    parameters: JSON.parse(JSON.stringify(this.paramSet))
                }
                this.$http.post('start', payload)
                    .then(response => {
                        console.log(response);
                        this.loading = false;
                    }, response => {
                        console.log(response);
                        this.loading = false;
                        alert(response.data.message);
                    });
            }
        }
    }
</script>
