import Vue from 'vue'
import VueResource from 'vue-resource'
import './plugins/vuetify'
import App from './App.vue'
import router from './router'

Vue.config.productionTip = false

Vue.use(VueResource);
Vue.http.options.root = process.env.NODE_ENV === 'production' ? '/jemo/admin' : 'http://localhost/jemo/admin';

new Vue({
  router,
  render: h => h(App)
}).$mount('#app')
