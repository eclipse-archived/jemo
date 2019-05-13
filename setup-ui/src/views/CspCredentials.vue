<template>

    <v-container>
        <v-container grid-list-md>
            <v-layout row wrap>

                <v-card flat class="text-xs-center ma-3">
                    <v-card-title primary-title>
                        <div>
                            <h3 class="headline mb-0">
                                {{csp.name + (isJemoUser ? ' jemo' : ' terraform')}} user credentials
                            </h3>
                        </div>
                    </v-card-title>

                    <v-card-text>
                        <v-form ref="form" class="mx-4">
                            <v-text-field v-for="credential in csp.requiredCredentials" :key="credential"
                                          v-model="parameters[credential]"
                                          :label="credential"
                                          required>
                            </v-text-field>

                            <v-container fluid>
                                <v-layout row wrap align-center>
                                    <v-flex xs6>
                                        <v-subheader>Select Region</v-subheader>
                                    </v-flex>

                                    <v-flex xs6>
                                        <v-select
                                                v-model="region"
                                                :hint="region.description"
                                                :items="csp.regions"
                                                item-text="code"
                                                item-value="description"
                                                label="Region"
                                                persistent-hint
                                                return-object
                                                single-line>
                                        </v-select>
                                    </v-flex>

                                </v-layout>
                                <v-layout row wrap align-center>
                                    <v-flex xs6>
                                        <v-subheader>Or Add a new Region Code</v-subheader>
                                    </v-flex>

                                    <v-flex xs6>
                                        <v-form>
                                            <v-text-field
                                                    v-model="regionCode"
                                                    required>
                                            </v-text-field>
                                        </v-form>
                                    </v-flex>

                                </v-layout>
                            </v-container>
                        </v-form>
                    </v-card-text>
                    <v-card-actions>
                        <v-btn class="mx-4" @click="validateCredentials" color="primary">
                            Submit
                        </v-btn>
                        <v-btn v-if="!isJemoUser" class="mx-4" @click="admin_user_info_dialog = true" color="secondary">
                            I don't have this info
                        </v-btn>
                    </v-card-actions>
                </v-card>

            </v-layout>
        </v-container>

        <v-dialog v-model="permissions_error_dialog" persistent max-width="600px" class="mx-1" scrollable>
            <v-list>
                <h3>The {{csp.name}} user does not have the following required permissions:</h3>

                <v-list-tile v-for="error in permission_errors" :key="error">
                    <v-list-tile-content>
                        <v-list-tile-title v-text="error"></v-list-tile-title>
                    </v-list-tile-content>
                </v-list-tile>
            </v-list>
            <v-btn @click="permissions_error_dialog = false">Close</v-btn>
        </v-dialog>

        <v-dialog v-model="admin_user_info_dialog" persistent max-width="800px" class="mx-1">
            <v-card>
                <v-card-title>
                            {{csp.adminUserCreationInstructions.description}}
                    Steps to follow:
                </v-card-title>

                <v-card-text v-for="step in csp.adminUserCreationInstructions.steps" :key="step">
                    {{step}}
                </v-card-text>
                <v-card-actions>
                    <v-btn @click="admin_user_info_dialog = false" color="primary">Close</v-btn>
                </v-card-actions>
            </v-card>
        </v-dialog>

    </v-container>
</template>

<script>
    export default {
        data() {
            return {
                csp: this.$route.params.csp,
                isJemoUser: this.$route.params.isJemoUser,
                parameters: {},
                credential_errors: null,
                region: this.$route.params.csp.region ? this.$route.params.csp.region : this.$route.params.csp.regions[0],
                regionCode: null,
                permission_errors: null,
                permissions_validated: false,
                permissions_error_dialog: false,
                admin_user_info_dialog: false
            }
        },
        watch: {
            '$route'(to) {
                if (to && to.name === 'csp-cred') {
                    this.csp = to.params.csp ? to.params.csp : this.csp;
                    this.isJemoUser = typeof to.params.isJemoUser !== 'undefined' ? to.params.isJemoUser : this.isJemoUser;
                    this.region = to.params.csp && to.params.csp.region ? to.params.csp.region : this.region;
                }
            }
        },
        methods: {
            validateCredentials: function () {
                let regionCode = this.regionCode ? this.regionCode : this.region.code;
                this.parameters['region'] = regionCode;
                const payload = {
                    csp: this.csp.name,
                    parameters: this.parameters
                };
                console.log(payload);
                this.$http.post('credentials', payload)
                    .then(response => {
                        console.log(response);
                        this.credential_errors = null;
                        this.csp['region'] = regionCode;
                        if (this.isJemoUser) {
                            this.validatePermissions();
                        } else {
                            this.$router.push({
                                name: 'install',
                                params: {csp: this.csp, parameters: this.parameters}
                            })
                        }
                    }, response => {
                        console.log(response);
                        alert(response.data);
                        this.credential_errors = response.data;
                    });
            },
            validatePermissions() {
                this.$http.get('permissions/' + this.csp.name)
                    .then(response => {
                        console.log(response);
                        this.permissions_validated = true;
                        this.redirectToParamSet();
                    }, response => {
                        console.log(response);
                        if (response.status === '403') {
                            this.permission_errors = response.data;
                            this.permissions_error_dialog = true;
                        } else {
                            alert(response.data);
                        }
                    });
            },
            redirectToParamSet() {
                this.$http.get('jemoparams/paramsets/' + this.csp.name)
                    .then(response => {
                        console.log(response);
                        this.$router.push({name: 'jemo-param-set', params: {csp: this.csp, paramSets: response.data}})
                    }, response => {
                        console.log(response);
                        alert(response.data);
                    });
            }
        }
    }
</script>
