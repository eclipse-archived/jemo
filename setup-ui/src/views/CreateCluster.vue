<template>

    <v-container grid-list-md class="ma-5">
        <div v-if="!downloadFormsOnly && !clusterCreated && !error">
            <h3>Please wait while Jemo creates the cluster. This can take up to 15 minutes.</h3>
            <v-progress-linear :indeterminate="true"></v-progress-linear>
            <div v-if="clusterCreationResponse">
                <pre>{{ clusterCreationResponse.output }}</pre>
                <div class="pa-3">
                    <v-progress-circular indeterminate :size="16"></v-progress-circular>
                </div>
            </div>
        </div>

        <div v-if="error">
            <div v-if="error.code === 'TERRAFORM_NOT_INSTALLED'">
                <h3>The server can not find the terraform command.</h3>
                Either terraform is not installed (<a
                    href="https://learn.hashicorp.com/terraform/getting-started/install.html" target="_blank">Installation
                instructions</a>),
                or it is not in the path. (e.g. run: terraform -version).
                <br/>
                <br/>
                <v-btn @click="createCluster(true)" color="primary" :loading="loading">Fixed</v-btn>
            </div>
            <div v-else-if="error.code === 'KUBECTL_NOT_INSTALLED'">
                <h3>The server can not find the kubectl command.</h3>
                Either kubectl is not installed (<a
                    href="https://kubernetes.io/docs/tasks/tools/install-kubectl/" target="_blank">Installation
                instructions</a>),
                or it is not in the path. (e.g. run: kubectl --help).
                <br/>
                <br/>
                <v-btn @click="createCluster(true)" color="primary" :loading="loading">Fixed</v-btn>
            </div>
            <div v-else>
                <h3>Failed to create the cluster. The following error occurred:</h3>
                <pre>{{error.message}}</pre>

                <br>
                <v-btn :to="{name: 'delete', params: {csp: this.csp, mode: 'CLUSTER'}}" color="primary">Revert</v-btn>
            </div>
        </div>

        <div v-if="clusterCreated">
            <h3>Setup Completed</h3>
            <br/>
            Great job! The cluster is created.
            <br/>
            You can access jemo on <a :href=clusterCreationResponse.loadBalancerUrl target="_blank">{{clusterCreationResponse.loadBalancerUrl}}</a>.
            You may need to wait 1-2 minutes before accessing it.
            <br/>
            <br/>
            Terraform reported the following created resources and outputs.
            <br/>
            <br/>

            <v-toolbar flat color="white">
                <v-toolbar-title>Created Resources</v-toolbar-title>
            </v-toolbar>
            <v-data-table
                    :items=createItems(clusterCreationResponse.terraformResult.createdResources)
                    class="elevation-1"
                    hide-actions
                    hide-headers
            >
                <template slot="items" slot-scope="props" >
                    <td>{{ props.item.name }}</td>
                    <td>{{ props.item.value }}</td>
                </template>
            </v-data-table>

            <br/>

            <v-toolbar flat color="white">
                <v-toolbar-title>Outputs</v-toolbar-title>
            </v-toolbar>
            <v-data-table
                    :items=createItems(clusterCreationResponse.terraformResult.outputs)
                    class="elevation-1"
                    hide-actions
                    hide-headers
            >
                <template slot="items" slot-scope="props" >
                    <td>{{ props.item.name }}</td>
                    <td>{{ props.item.value }}</td>
                </template>
            </v-data-table>

            <br/>

        </div>

        <div v-if="terraformFilesDownloaded">
            <h3>Please unzip the downloaded file, open the file 'README.txt' and follow the instructions.</h3>
        </div>

    </v-container>
</template>

<script>
    export default {
        data() {
            return {
                csp: this.$route.params.csp,
                parameters: this.$route.params.parameters,
                downloadFormsOnly: this.$route.params.downloadFormsOnly,
                clusterCreated: false,
                error: null,
                clusterCreationResponse: null,
                terraformFilesDownloaded: false,
                timer: null,
                loading: false
            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'create-cluster') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.parameters = to.params.parameters ? to.params.parameters : this.parameters;
                    this.downloadFormsOnly = to.params.downloadFormsOnly;
                    this.clusterCreated = false;
                    this.loading = false;
                    this.createCluster(false);
                }
            }
        },
        mounted() {
            this.createCluster(false);
        },
        methods: {
            createCluster(fixedPressed) {
                this.loading = fixedPressed;
                const payload = {
                    csp: this.csp.name,
                    parameters: this.parameters
                };

                if (this.downloadFormsOnly) {
                    this.$http.post('cluster/download', payload, {responseType: 'blob'})
                        .then(response => {
                            return response.blob();
                        }).then(blob => {
                        const a = document.createElement("a");
                        document.body.appendChild(a);
                        const url = window.URL.createObjectURL(blob);
                        a.href = url;
                        a.download = 'cluster.zip';
                        a.click();
                        window.URL.revokeObjectURL(url);
                        a.remove();
                        this.terraformFilesDownloaded = true;
                    });
                } else {
                    this.$http.post('cluster', payload)
                        .then(response => {
                            console.log(response);
                            this.error = null;
                            this.timer = setInterval(this.pollForClusterCreationResult, 10000);
                        }, response => {
                            console.log(response);
                            this.error = response.data;
                            this.loading = false;
                        });
                }
            },
            pollForClusterCreationResult() {
                this.$http.get('cluster/result')
                    .then(response => {
                        console.log(response);
                        this.clusterCreationResponse = response.data;
                        if (response.data.status === 'FINISHED') {
                            clearInterval(this.timer);
                            if (response.data.error) {
                                this.error = response.data.error;
                            } else {
                                this.clusterCreated = true;
                                this.error = null;
                            }
                        }
                    }, response => {
                        clearInterval(this.timer);
                        console.log(response);
                        this.error = response.data;
                        alert(response.data);
                    });
            },
            createItems(jsonObject) {
                const items = [];
                for (let key in jsonObject) {
                    items.push({
                        name: key,
                        value: jsonObject[key]
                    });
                }
                console.log(items);
                return items;
            }
        }
    }
</script>
