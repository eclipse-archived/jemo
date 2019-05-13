<template>
    <div class="home">

        <v-container grid-list-md>
            <v-layout row wrap>
                <v-flex xs12 sm6 md4 lg3 v-for="supportedCSP in supportedCSPs" :key="supportedCSP.name">
                    <v-card flat class="text-xs-center ma-3" @click="onCspSelected(supportedCSP)" style="cursor: pointer;">
                        <v-card-title primary-title class="justify-center">
                            <h3 class="headline justify-center mb-0">{{supportedCSP.name}}</h3>
                        </v-card-title>
                        <v-card-text>
                            <a v-if="supportedCSP.url" :href="supportedCSP.url" target="_blank">{{supportedCSP.description}}</a>
                            <div v-else>{{supportedCSP.description}}</div>
                        </v-card-text>
                    </v-card>
                </v-flex>
            </v-layout>
        </v-container>

    </div>
</template>

<script>

    export default {
        data() {
            return {
                supportedCSPs: []
            };
        },
        created() {
            this.$http.get('init')
                .then(response => {
                    console.log(response);
                    this.supportedCSPs = response.data
                }, response => {
                    console.log(response);
                });
        },
        methods: {
            onCspSelected(csp) {
                const payload = {
                    csp: csp.name
                }
                this.$http.put('csp/' + csp.name, payload)
                    .then(response => {
                        console.log(response);
                        if (csp.name === 'MEMORY') {
                            this.redirectToParamSet(csp);
                        } else {
                            this.$router.push({name: 'csp', params: {csp: csp}})
                        }
                    }, response => {
                        console.log(response);
                        alert(response.data);
                    });
            },
            redirectToParamSet(csp) {
                this.$http.get('jemoparams/paramsets/' + csp.name)
                    .then(response => {
                        console.log(response);
                        this.$router.push({name: 'jemo-param-set', params: {csp: csp, paramSets: response.data}})
                    }, response => {
                        console.log(response);
                        alert(response.data);
                    });
            }
        }
    }
</script>
