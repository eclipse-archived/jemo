<template xmlns:v-slot="http://www.w3.org/1999/XSL/Transform">
    <div>

        <v-spacer></v-spacer>
        <div class="d-flex justify-between align-center mb-3">
            <v-btn @click="getPlugins" color="primary">Refresh</v-btn>
            <v-btn @click="expandAll" color="secondary">Expand All</v-btn>
            <v-btn @click="colapseAll" color="green">Collapse All</v-btn>
        </div>

        <div v-if="plugins && plugins.length > 0">
            <h4>Running Plugins</h4>
            <br/>
        </div>

        <v-expansion-panel
                v-model="expand"
                expand>
            <v-expansion-panel-content
                    v-for="(plugin,i) in plugins"
                    :key="i">
                <template v-slot:header>
                    <div><strong>id:</strong> {{plugin.pluginInfo.id}}</div>
                    <div><strong>name:</strong> {{plugin.pluginInfo.name}}</div>
                    <div><strong>version:</strong> {{plugin.pluginInfo.version}}</div>
                    <v-btn @click="changePluginVersionStatus(i, plugin.pluginInfo.id, plugin.pluginInfo.version, plugin.metaData.enabled)"
                           :loading="loading[plugin.pluginInfo.id + '-' + plugin.pluginInfo.version + '-act']"
                           color="primary" flat
                    >{{plugin.metaData.enabled ? 'Deactivate' : 'Activate'}}
                    </v-btn>
                    <v-btn @click="alertForDeletion(plugin, i)"
                           :loading="loading[plugin.pluginInfo.id + '-' + plugin.pluginInfo.version + '-del']"
                           color="red" flat>Delete
                    </v-btn>
                </template>

                <v-data-table
                        :headers="[{
                        text: 'Endpoints',
                        align: 'left',
                        sortable: false,
                        value: 'endpoint'
                        }]"
                        :items="Object.keys(plugin.metaData.endpoints)"
                        class="elevation-1"
                        hide-actions>
                    <template slot="items" slot-scope="props">
                        <td><strong>class: </strong>{{ props.item }} - <strong>endpoint: </strong>{{
                            plugin.metaData.endpoints[props.item] }}
                        </td>
                        <td><strong>avg response time: </strong>{{ avg(plugin.metaData.stats[props.item]) }}</td>
                    </template>
                </v-data-table>

                <v-data-table
                        :headers="[{
                        text: 'Batches',
                        align: 'left',
                        sortable: false,
                        value: 'batch'
                        }]"
                        :items="plugin.metaData.batches"
                        class="elevation-1"
                        hide-actions>
                    <template slot="items" slot-scope="props">
                        <td><strong>class: </strong>{{ props.item }}</td>
                        <td><strong>avg response time: </strong>{{ avg(plugin.metaData.stats[props.item]) }}</td>
                    </template>
                </v-data-table>

                <v-data-table
                        :headers="[{
                        text: 'Events',
                        align: 'left',
                        sortable: false,
                        value: 'event'
                        }]"
                        :items="plugin.metaData.events"
                        class="elevation-1"
                        hide-actions>
                    <template slot="items" slot-scope="props">
                        <td><strong>class: </strong>{{ props.item }}</td>
                        <td><strong>avg response time: </strong>{{ avg(plugin.metaData.stats[props.item]) }}</td>
                    </template>
                </v-data-table>

                <v-data-table
                        :headers="[{
                        text: 'Fixed Processes',
                        align: 'left',
                        sortable: false,
                        value: 'event'
                        }]"
                        :items="plugin.metaData.fixed"
                        class="elevation-1"
                        hide-actions>
                    <template slot="items" slot-scope="props">
                        <td><strong>class: </strong>{{ props.item }}</td>
                    </template>
                </v-data-table>

            </v-expansion-panel-content>
        </v-expansion-panel>

        <v-spacer></v-spacer>

        <br/>
        <br/>

        <div v-if="pluginsPendingDeployment && pluginsPendingDeployment.length > 0">
            <h4>Deployment History</h4>
            <br/>

            <v-expansion-panel
                    v-model="expandPending"
                    expand>
                <v-expansion-panel-content
                        v-for="(plugin,i) in pluginsPendingDeployment"
                        :key="i">
                    <template v-slot:header>
                        <div><strong>id:</strong> {{plugin.pluginId}}</div>
                        <div><strong>version:</strong> {{plugin.version}}</div>
                        <div><strong>name:</strong> {{plugin.name}}</div>
                        <div><strong>time:</strong> {{plugin.timestamp}}</div>
                        <div><strong>repoUrl:</strong> {{plugin.repoUrl}}</div>
                        <div><strong>branch:</strong> {{plugin.branch}}</div>
                        <div><strong>subDir:</strong> {{plugin.subDir === '' ? '-' : plugin.subDir}}</div>
                        <v-icon v-if="plugin.success" color="teal">done</v-icon>
                        <v-icon v-else color="error">error</v-icon>
                    </template>

                    <v-data-table
                            :headers="[{
                        text: 'Logs',
                        align: 'left',
                        sortable: false,
                        value: 'batch'
                        }]"
                            :items="Array.of(plugin.logs)"
                            class="elevation-1"
                            hide-actions>
                        <template slot="items" slot-scope="props">
                            <td>
                                <pre>{{ props.item }}</pre>
                            </td>
                        </template>
                    </v-data-table>
                </v-expansion-panel-content>
            </v-expansion-panel>
        </div>

        <v-dialog v-model="deleteConfirmationDialog" persistent max-width="400px" class="mx-1">
            <v-card>
                <v-card-text>
                    Are you sure you want to delete this plugin?
                </v-card-text>
                <v-card-actions>
                    <v-spacer></v-spacer>
                    <v-btn color="primary" flat
                           @click="deletePluginVersion">OK
                    </v-btn>
                    <v-btn color="error" flat
                           @click="deleteConfirmationDialog = false">Cancel
                    </v-btn>
                </v-card-actions>
            </v-card>
        </v-dialog>

    </div>
</template>

<script>
    export default {
        data() {
            return {
                expand: [],
                expandPending: [],
                plugins: null,
                headers: this.$route.params.headers,
                loading: {},
                pluginsPendingDeployment: [],
                deleteConfirmationDialog: false,
                pluginToDelete: null
            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'plugins') {
                    this.getPlugins();
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
                        this.plugins = response.data;
                    }, response => {
                        console.log(response);
                        // alert(response.data ? response.data : 'Wrong username or password.');
                    });
                this.pollForPendingDeployments();
            },
            pollForPendingDeployments() {
                this.$http.get('cicd/result', {headers: this.headers})
                    .then(response => {
                        console.log(response);
                        this.pluginsPendingDeployment = response.data;
                    }, response => {
                        console.log(response);
                        alert(response.data ? response.data : 'Wrong username or password.');
                    });
            },
            expandAll() {
                this.expand = [...Array(this.plugins.length).keys()].map(() => true);
                this.expandPending = [...Array(this.pluginsPendingDeployment.length).keys()].map(() => true);
            },
            colapseAll() {
                this.expand = [];
                this.expandPending = [];
            },
            changePluginVersionStatus(index, pluginId, pluginVersion, currentState) {
                this.expand[index] = !this.expand[index]
                const label = pluginId + '-' + pluginVersion + '-act';
                this.loading[label] = true;
                const payload = {
                    enabled: !currentState
                };
                this.$http.patch('plugins/' + pluginId + '/' + pluginVersion, payload, {headers: this.headers})
                    .then(response => {
                        this.loading[label] = false;
                        console.log(response);
                        if (response.status === 200) {
                            this.plugins = this.plugins.map(plugin => plugin.metaData.id === response.data.metaData.id ? response.data : plugin);
                        }
                    }, response => {
                        this.loading[label] = false;
                        alert(response.data);
                        console.log(response);
                    });
            },
            alertForDeletion(plugin, index) {
                this.expand[index] = !this.expand[index]
                this.pluginToDelete = {
                    plugin: plugin,
                    index: index
                }
                this.deleteConfirmationDialog = true;
            },
            deletePluginVersion() {
                this.expand[index] = !this.expand[index]

                this.deleteConfirmationDialog = false;
                const plugin = this.pluginToDelete.plugin;
                const index = this.pluginToDelete.index;
                this.pluginToDelete = null;

                const label = plugin.pluginInfo.id + '-' + plugin.pluginInfo.version + '-del';
                this.loading[label] = true;
                this.$http.delete('plugins/' + plugin.pluginInfo.id + '/' + plugin.pluginInfo.version, {headers: this.headers})
                    .then(response => {
                        this.loading[label] = false;
                        console.log(response);
                        if (response.status === 204) {
                            this.plugins.splice(index, 1);
                        }
                    }, response => {
                        this.loading[label] = false;
                        alert(response.data);
                    });
            },
            avg(accumulator) {
                return accumulator ? (accumulator.totalTime / accumulator.samples).toFixed(2) : 0;
            }
        }
    }
</script>
