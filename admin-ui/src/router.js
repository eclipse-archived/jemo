import Vue from 'vue'
import Router from 'vue-router'
import Home from './views/Home'
import Plugins from './views/Plugins'
import CICD from './views/CICD'

Vue.use(Router)

export default new Router({
  routes: [
    {path: '/', name: 'home', component: Home},
    {path: '/plugins', name: 'plugins', component: Plugins},
    {path: '/cicd', name: 'cicd', component: CICD},
    {path: '*', redirect: { name: 'home' }}
  ]
})
