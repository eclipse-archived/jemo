<template>

    <v-container grid-list-md class="ma-5">
        <div v-if="!deletionComplete && !error">
            <h3>Please wait while Jemo deletes {{this.csp.name}} resources.</h3>
            <v-progress-linear :indeterminate="true"></v-progress-linear>
            <div v-if="terraformOutput">
                <pre>{{terraformOutput}}</pre>
                <div class="pa-3">
                    <v-progress-circular indeterminate :size="16"></v-progress-circular>
                </div>
            </div>
        </div>

        <div v-if="error">
            <div v-if="error.message">
                <h3>Terraform failed to delete the {{this.csp.name}} resources. The following error occurred:</h3>
                <pre>{{error.message}}</pre>
            </div>
            <div>
                <h3>Deleting the {{this.csp.name}} resources failed. The following error occurred:</h3>
                <pre>{{error}}</pre>
            </div>
            <br/>
            <v-btn @click="deleteResources" color="primary">Retry</v-btn>
        </div>

        <div v-if="deletionComplete">
            <h3>The {{this.csp.name}} resources are deleted</h3>
            <br/>

            <div>
                <pre>{{terraformOutput}}</pre>
            </div>

        </div>

    </v-container>
</template>

<script>
    export default {
        data() {
            return {
                csp: this.$route.params.csp,
                mode: this.$route.params.mode,
                deletionComplete: false,
                error: null,
                terraformResult: null,
                terraformOutput: null,
                loading: false,
                timer: null
            }
        },
        watch: {
            '$route'(to) {
                if (to.name === 'delete') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.mode = to.params.mode ? to.params.mode : this.mode;
                    if (to.params.csp) {
                        this.deleteResources();
                    }
                }
            }
        },
        mounted() {
            this.deleteResources();
        },
        methods: {
            init() {
                this.deletionComplete = false;
                this.loading = false;
                this.error = null;
                this.terraformResult = null;
                this.terraformOutput = null;
            },
            deleteResources() {
                this.init();
                this.loading = true;

                if (this.mode === 'CLUSTER') {
                    this.deleteResourcesByKind('cluster');
                } else if (this.mode === 'INSTALL') {
                    this.deleteResourcesByKind('install');
                }
            },
            deleteResourcesByKind(kind) {
                this.$http.delete(kind + '/' + this.csp.name)
                    .then(response => {
                        console.log(response);
                        this.timer = setInterval(this.pollForDeletionResult, 10000, kind);
                    }, response => {
                        console.log(response);
                        this.error = response.data;
                    });
            },
            pollForDeletionResult(resourceKind) {
                this.$http.get(resourceKind + '/result/' + this.csp.name)
                    .then(response => {
                        console.log(response);
                        this.terraformOutput = response.data.output;
                        if (response.data.status === 'FINISHED') {
                            clearInterval(this.timer);
                            if (response.data.error) {
                                this.error = response.data.error;
                            } else {
                                this.deletionComplete = true;
                                this.error = null;
                                this.terraformResult = response.data.terraformResult;
                            }
                        }
                    }, response => {
                        clearInterval(this.timer);
                        console.log(response);
                        this.error = response.data;
                        alert(response.data);
                    });
            }
        },
        filters: {
            pretty: function (value) {
                return JSON.stringify(value, null, 2);
            }
        }
    }
</script>
