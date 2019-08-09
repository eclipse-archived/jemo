<template>

    <div>
        <v-container grid-list-md>

            <v-layout row wrap v-if="!response && !error">

                <v-flex xs12 sm10 md9 lg7>
                    <v-card flat class="text-xs-center ma-3">
                        <v-card-title primary-title>
                            <div>
                                <h3 class="headline mb-0">
                                    Select a Github repository with the code
                                    of a Jemo plugin to deploy from.
                                </h3>
                            </div>
                        </v-card-title>

                        <v-card-text>
                            <v-form ref="form" class="mx-4">
                                <v-text-field v-model="username" label="username*"
                                              @focusout="getRepositories"></v-text-field>
                                <v-text-field v-model="password" label="password**"
                                              @focusout="getRepositories"
                                ></v-text-field>

                                <v-select v-if="repositories.length>0"
                                          v-model="selectedRepository"
                                          :items="repositories"
                                          label="repositories*"
                                          @change="getBranches">
                                </v-select>

                                <v-select v-if="branches.length>0"
                                          v-model="selectedBranch"
                                          :items="branches"
                                          label="branches">
                                </v-select>

                                <v-text-field v-if="branches.length>0" v-model="subDirectory"
                                              label="sub-directory"></v-text-field>

                                <v-text-field v-if="selectedRepository" v-model="pluginId"
                                              label="pluginId***"></v-text-field>

                                <v-switch v-if="branches.length>0" v-model="skipTests"
                                          color="indigo"
                                          label="Skip tests"
                                          hide-details>
                                </v-switch>

                                <v-card-text>(*) Mandatory field</v-card-text>
                                <v-card-text>(**) If the github account has 2FA enabled, you need to provide the
                                    <a href="https://github.com/settings/tokens/new" target="_blank">personal auth token</a>,
                                    rather than the password.</v-card-text>
                                <v-card-text v-if="selectedRepository">(***) Unique App identifier. For apps deployed
                                    for the first time use the displayed value.
                                    Otherwise, use the same id as in the first time.
                                </v-card-text>
                            </v-form>
                        </v-card-text>
                        <v-card-actions>
                            <v-btn @click="deploy" :loading="loading" :disabled="!validForm" color="primary">
                                Submit
                            </v-btn>
                        </v-card-actions>
                    </v-card>
                </v-flex>
            </v-layout>

        </v-container>

        <div v-if="response">
            <h3>{{response.msg}}</h3>
            <br/>
            The following logs were produced:
            <br/>
            <br/>
            <pre>{{response.logs}}</pre>
        </div>

        <div v-if="error">
            <h3>{{error.msg}}</h3>

            <div v-if="error.logs">
                <br/>
                The following logs were produced:
                <br/><br/>
                <pre>{{error.logs}}</pre>
            </div>
        </div>

    </div>
</template>

<script>
    export default {
        data() {
            return {
                headers: this.$route.params.headers,
                service: 'git',
                username: null,
                password: null,
                prevUsername: null,
                prevPassword: null,
                repositories: [],
                selectedRepository: null,
                branches: [],
                selectedBranch: null,
                subDirectory: '',
                skipTests: false,
                pluginId: null,
                loading: false,
                response: null,
                error: null
            }
        },
        computed: {
            validForm() {
                return this.username && this.selectedRepository && this.pluginId;
            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'cicd') {
                    this.loading = false;
                    this.response = null;
                    this.error = null;
                }
            }
        },
        created() {
            this.getPlugins();
        },
        methods: {
            getPlugins() {
                this.$http.get('plugins', {headers: this.headers})
                    .then(response => {
                        console.log(response);
                        const pluginIds = response.data.map(plugin => plugin.pluginInfo.id);
                        this.pluginId = pluginIds.length > 0 ? Math.max(...pluginIds) + 1 : 2;
                    }, response => {
                        console.log(response);
                        alert(response.data ? response.data : 'Wrong username or password.');
                    });
            },
            clear() {
                this.repositories = [];
                this.branches = [];
                this.selectedRepository = null;
                this.selectedBranch = null;
                this.subDirectory = '';
            },
            getRepositories() {
                if (this.prevUsername === this.username && this.prevPassword === this.password) {
                    return;
                }
                this.prevPassword = this.password;
                this.prevUsername = this.username;

                this.clear();

                const gitHeaders = this.password ? {
                    'Authorization': 'Basic ' + window.btoa(this.username + ':' + this.password)
                } : {};
                this.$http.get('https://api.github.com/users/' + this.username + '/repos', {headers: gitHeaders})
                    .then(response => {
                        this.repositories = response.data.map(repo => repo.name);
                        console.log(response);
                    }, response => {
                        console.log(response);
                        alert('No gihub repository found for this username/password.\n\n' + JSON.stringify(response.data));
                    });
            },
            getBranches() {
                const gitHeaders = this.password ? {
                    'Authorization': 'Basic ' + window.btoa(this.username + ':' + this.password)
                } : {};
                this.$http.get('https://api.github.com/repos/' + this.username + '/' + this.selectedRepository + '/branches', {headers: gitHeaders})
                    .then(response => {
                        this.branches = response.data.map(repo => repo.name);
                        console.log(response);
                    }, response => {
                        console.log(response);
                    });
            },
            deploy() {
                this.loading = true;
                this.response = null;
                this.error = null;
                const payload = {
                    service: this.service,
                    repoUrl: 'https://github.com/' + this.username + '/' + this.selectedRepository + '.git',
                    branch: this.selectedBranch ? this.selectedBranch : 'master',
                    subDir: this.subDirectory,
                    pluginId: this.pluginId,
                    skipTests: this.skipTests
                };
                this.$http.post('cicd', payload, {headers: this.headers})
                    .then(response => {
                        console.log(response);
                        this.loading = false;
                        this.response = response.data;
                        this.$router.push({
                            name: 'plugins',
                            params: {headers: this.headers}
                        })
                    }, response => {
                        if (response.data.logs) {
                            console.log(response);
                            this.error = response.data;
                        } else {
                            alert(response.data.msg);
                        }
                        this.loading = false;
                    });
            }
        }
    }
</script>
