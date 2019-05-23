<template xmlns:v-slot="http://www.w3.org/1999/XSL/Transform">
  <div>

    <v-form v-if="!isAuthorised">
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
          <v-btn class="mx-4" @click="authorise" color="primary">Submit</v-btn>
        </v-layout>
      </v-container>
    </v-form>



    <div v-else>
      <v-spacer></v-spacer>
      <v-container grid-list-md>
        <v-layout row wrap>

        <v-card flat class="text-xs-left ma-3">
          <v-card-title primary-title>
            <div>
              <h3 class="headline mb-0">Admin Panel</h3>
            </div>
          </v-card-title>

          <v-card-text>
                Monitor the plugins that are currently deployed. You can de-activate and re-active plugin versions
                or even delete (undeploy) them.
          </v-card-text>
          <v-card-actions>
            <v-btn class="mx-2" :to="{name: 'plugins', params: {headers: this.headers}}" color="primary">
                    Admin Panel
            </v-btn>
          </v-card-actions>
        </v-card>

        <v-card flat class="text-xs-left ma-3">
            <v-card-title primary-title>
                <div>
                    <h3 class="headline mb-0">CI/CD</h3>
                </div>
            </v-card-title>

            <v-card-text>
              Deploy Jemo plugins by pointing to a github repository.
              You need to have <a href="https://git-scm.com/" target="_blank">git</a>
              and <a href="https://maven.apache.org/" target="_blank">mvn</a> installed and on your Path to use this functionality.
              For an example of a Jemo plugin see the <a href="https://github.com/eclipse/jemo/tree/master/demos/jemo-trader-app" target="_blank">jemo-trader-app</a>.
            </v-card-text>
            <v-card-actions>
                <v-btn class="mx-2" :to="{name: 'cicd', params: {headers: this.headers}}" color="secondary">
                    CI/CD
                </v-btn>
            </v-card-actions>
        </v-card>

    </v-layout>
</v-container>
    </div>

  </div>
</template>

<script>
    export default {
        data() {
            return {
                isAuthorised: false,
                username: "system.administrator@jemo.eclipse.org",
                password: null,
                headers: null
            }
        },
        methods: {
            authorise() {
              this.headers = {
                'Authorization': 'Basic ' + window.btoa(this.username + ':' + this.password)
              };
              this.$http.post('auth', {}, {headers: this.headers})
                  .then(response => {
                      console.log(response);
                      this.isAuthorised = true;
                  }, response => {
                      console.log(response);
                      alert(response.data ? response.data : 'Wrong username or password.');
                  });
            }
        }
    }
</script>
