<template>

    <v-container grid-list-md>
        <v-layout row wrap>

            <v-card flat class="text-xs-left ma-3">
                <v-card-title primary-title>
                    <div>
                        <h3 class="headline mb-0">Use an existing {{csp.name}} jemo user</h3>
                    </div>
                </v-card-title>

                <v-card-text>
                    Select this option if you want to start Jemo with an existing user.
                    Jemo asks you for the user's credentials and checks if the user
                    has the required permissions.
                    If not, Jemo lists the missing permissions, add them and retry to login.
                </v-card-text>
                <v-card-actions>
                    <v-btn class="mx-2" :to="{name: 'csp-cred', params: {csp: csp, isJemoUser: true}}" color="primary">
                        Login
                    </v-btn>
                </v-card-actions>
            </v-card>

            <v-card flat class="text-xs-left ma-3">
                <v-card-title primary-title>
                    <div>
                        <h3 class="headline mb-0">Jemo Installation</h3>
                    </div>
                </v-card-title>

                <v-card-text>
                    Select this option if you want Jemo to generate a new user with the required permissions, along with other installation resources.
                    Credentials for a terraform user with Admin permissions are required, as these are needed to create the required CSP resources.
                    Internally, Jemo generates <a href="https://www.terraform.io/" target="_blank">Terraform</a> templates and runs them.
                </v-card-text>
                <v-card-actions>
                    <v-btn class="mx-2" :to="{name: 'csp-cred', params: {csp: csp, isJemoUser: false}}" color="secondary">
                        Install
                    </v-btn>
                </v-card-actions>
            </v-card>

            <v-card flat class="text-xs-left ma-3">
                <v-card-title primary-title>
                    <div>
                        <h3 class="headline mb-0">Download the Terraform templates</h3>
                    </div>
                </v-card-title>

                <v-card-text>
                    Similar to the previous option, except that the <a href="https://www.terraform.io/" target="_blank">Terraform</a>
                    templates are not run by Jemo.
                    Instead, you download the templates so that you can run them locally.
                </v-card-text>
                <v-card-actions>
                    <v-btn class="mx-2" @click="downloadTemplates()" color="error">
                        Download
                    </v-btn>
                </v-card-actions>
            </v-card>

            <v-card flat class="text-xs-left ma-3">
                <v-card-title primary-title>
                    <div>
                        <h3 class="headline mb-0">Delete Existing Resources</h3>
                    </div>
                </v-card-title>

                <v-card-text>
                    Delete resources created by Jemo, if you don't need them any longer.
                </v-card-text>
                <v-card-actions>
                    <v-btn class="mx-2" :to="{name: 'delete', params: {csp: this.csp, mode: 'INSTALL'}}">Delete Installation Resources</v-btn>
                    <v-btn class="mx-2" :to="{name: 'delete', params: {csp: this.csp, mode: 'CLUSTER'}}">Delete Cluster Resources</v-btn>
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
            }
        },
        watch: {
            '$route.params.csp' (to) {
                if (to) {
                    this.csp = to;
                }
            }
        },
        methods: {
            downloadTemplates() {
                const payload = {
                    csp: this.csp.name,
                };
                this.$http.post('install/download', payload, {responseType: 'blob'})
                    .then(response => {
                        return response.blob();
                    }).then(blob => {
                    const a = document.createElement("a");
                    document.body.appendChild(a);
                    const url = window.URL.createObjectURL(blob);
                    a.href = url;
                    a.download = 'install.zip';
                    a.click();
                    window.URL.revokeObjectURL(url);
                    a.remove();
                });

                if (this.csp.installProperties) {
                    this.$router.push({name: 'install-props', params: {csp: this.csp}})
                }
            }
        }
    }
</script>
