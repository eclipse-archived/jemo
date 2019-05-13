<template>
    <v-app>
        <navbar/>

        <v-content class="mx-4 mb-4">
            <keep-alive>
                <router-view></router-view>
            </keep-alive>
        </v-content>
    </v-app>
</template>

<script>
    import Navbar from './components/Navbar';

    export default {
        name: 'App',
        components: {
            Navbar
        },
        data() {
            return {
                //
            }
        }
    }
</script>
