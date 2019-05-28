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
                          <v-text-field v-model="repoUrl" label="repoUrl"></v-text-field>
                          <v-text-field v-model="branch" label="branch"></v-text-field>
                          <v-text-field v-model="subDir" label="subDir"></v-text-field>
                          <v-text-field v-model="pluginId" label="pluginId"></v-text-field>
                        </v-form>
                    </v-card-text>
                    <v-card-actions>
                        <v-btn @click="deploy" :loading="loading" color="primary">
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
                repoUrl: null,
                branch: null,
                subDir: null,
                pluginId: null,
                loading: false,
                response: null,
                error: null
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
        methods: {
            deploy() {
              this.loading = true;
              this.response = null;
              this.error = null;
                const payload = {
                  service: this.service,
                  repoUrl: this.repoUrl,
                  branch: this.branch,
                  subDir: this.subDir,
                  pluginId: this.pluginId
                };
                this.$http.post('cicd', payload, {headers: this.headers})
                    .then(response => {
                        console.log(response);
                        this.loading = false;
                        this.response = response.data;
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
