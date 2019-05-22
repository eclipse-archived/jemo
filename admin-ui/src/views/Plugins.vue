<template xmlns:v-slot="http://www.w3.org/1999/XSL/Transform">
  <div>

    <v-form v-if="!plugins">
  <v-container>
    <v-layout row wrap>

      <v-flex xs12 sm6 md3>
        <v-text-field
          label="Jemo username"
          v-model="username"
        ></v-text-field>
      </v-flex>

      <v-flex xs12 sm6 md3>
        <v-text-field
          label="Jemo password"
          v-model="password"
          type="password"
        ></v-text-field>
      </v-flex>

      <v-btn class="mx-4" @click="getPlugins" color="primary">
          Submit
      </v-btn>

    </v-layout>
  </v-container>
</v-form>



    <div v-else>
        <v-spacer></v-spacer>
        <div class="d-flex justify-between align-center mb-3">
          <v-btn @click="getPlugins" color="primary">Refresh</v-btn>
          <v-btn @click="expandAll" color="secondary">Expand All</v-btn>
            <v-btn @click="colapseAll" color="green">Collapse All</v-btn>
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
                    <v-btn @click.stop="changePluginVersionStatus(plugin.pluginInfo.id, plugin.pluginInfo.version, plugin.metaData.enabled)"
                           color="primary" flat
                    >{{plugin.metaData.enabled ? 'Deactivate' : 'Activate'}}
                    </v-btn>
                    <v-btn @click.stop="deletePluginVersion(plugin, i)" color="red" flat>Delete</v-btn>
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
                        <td>{{ props.item }}</td>
                        <td>{{ plugin.metaData.endpoints[props.item] }}</td>
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
                        <td>{{ props.item }}</td>
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
                        <td>{{ props.item }}</td>
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
                        <td>{{ props.item }}</td>
                    </template>
                </v-data-table>

            </v-expansion-panel-content>
        </v-expansion-panel>
    </div>

  </div>
</template>

<script>
    export default {
        data() {
            return {
                expand: [],
                plugins: null,
                username: "system.administrator@jemo.eclipse.org",
                password: null
            }
        },
        created() {
        },
        methods: {
            getPlugins() {
              const headers = {
                'Authorization': 'Basic ' + window.btoa(this.username + ':' + this.password)
              };
              this.$http.get('/jemo/admin/plugins', {headers: headers})
                  .then(response => {
                      console.log(response);
                      this.plugins = response.data;
                  }, response => {
                      console.log(response);
                      alert(response.data ? response.data : 'Wrong username or password.');
                  });
            },
            expandAll() {
                this.expand = [...Array(this.plugins.length).keys()].map(() => true);
            },
            colapseAll() {
                this.expand = [];
            },
            changePluginVersionStatus(pluginId, pluginVersion, currentState) {
                const headers = {
                   'Authorization': 'Basic ' + window.btoa(this.username + ':' + this.password)
                };
                const payload = {
                    enabled: !currentState
                };
                this.$http.patch('/jemo/admin/plugins/' + pluginId + '/' + pluginVersion, payload, {headers: headers})
                    .then(response => {
                        console.log(response);
                        if (response.status === 200) {
                            this.plugins = this.plugins.map(plugin => plugin.metaData.id === response.data.metaData.id ? response.data : plugin);
                        }
                    }, response => {
                        alert(response.data);
                        console.log(response);
                    });
            },
            deletePluginVersion(plugin, index) {
                const headers = {
                   'Authorization': 'Basic ' + window.btoa(this.username + ':' + this.password)
                };
                this.$http.delete('/jemo/admin/plugins/' + plugin.pluginInfo.id + '/' + plugin.pluginInfo.version, {headers: headers})
                    .then(response => {
                        console.log(response);
                        if (response.status === 204) {
                            this.plugins.splice(index, 1);
                        }
                    }, response => {
                        alert(response.data);
                    });
            }
        }
    }
</script>
