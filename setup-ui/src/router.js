import Vue from 'vue'
import Router from 'vue-router'
import Home from './views/Home.vue'

Vue.use(Router)

export default new Router({
    routes: [
        {path: '/', name: 'home', component: Home},
        {path: '/csp', name: 'csp', props: true, component: () => import('./views/Csp.vue')},
        {path: '/delete', name: 'delete', props: true, component: () => import('./views/Delete.vue')},
        {path: '/csp-cred', name: 'csp-cred', props: true, component: () => import('./views/CspCredentials.vue')},
        {path: '/install/props', name: 'install-props', props: true, component: () => import('./views/InstallProps.vue')},
        {path: '/install', name: 'install', props: true, component: () => import('./views/Install.vue')},
        {path: '/jemo/param-set', name: 'jemo-param-set', props: true, component: () => import('./views/JemoParamSet.vue')},
        {path: '/jemo/params', name: 'jemo-params', props: true, component: () => import('./views/JemoParams.vue')},
        {path: '/jemo/setup-complete', name: 'setup-complete', props: true, component: () => import('./views/SetupComplete.vue')},
        {path: '/prod/config', name: 'prod-conf', props: true, component: () => import('./views/ProdConf.vue')},
        {path: '/cluster/create', name: 'create-cluster', props: true, component: () => import('./views/CreateCluster.vue')},
        {path: '*', redirect: { name: 'home' }}
    ]
})
