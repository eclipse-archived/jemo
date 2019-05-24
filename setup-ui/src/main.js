import Vue from 'vue'
import VueResource from 'vue-resource'
import './plugins/vuetify'
import App from './App.vue'
import router from './router'

Vue.config.productionTip = false;

Vue.use(VueResource);
Vue.http.options.root = process.env.NODE_ENV === 'production' ? '/jemo/setup' : 'http://localhost/jemo/setup';

new Vue({
  router,
  render: h => h(App)
}).$mount('#app')
