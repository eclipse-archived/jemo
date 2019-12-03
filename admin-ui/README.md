# eclipse-jemo-admin-ui
This repository contains the ui code for the admin functionalities of Eclipse Jemo.

This UI allows the Jemo admin user to:
- Deploy a new Jemo application by pointing to the github repository hosting this application. Jemo assumes the gihub repository has a valid maven pom file and runs 'mvn deploy', which includes the compile and test maven targets. If compilation or one of the tests fail, deployment is refused.
- Monitor the running applications and Jemo modules whithin them, including the average execution time for each module.
- Deactivate/Reactivate/Delete Jemo applications.

This UI runs under https://JEMO_HOME/jemo/admin/ where JEMO_HOME is the IP or DNS where Jemo runs. This can be a local Jemo instance or an instance running on the cluster created by the setup wizzard.

This project is generated with Vue [CLI v3.5.0](https://cli.vuejs.org/) and the [Vue.js v2.6.6](https://vuejs.org/) framework.

## Project setup
```
npm install
```

### Compiles and hot-reloads for development
```
npm run serve
```

### Compiles and minifies for production
```
npm run build
```

### Run your tests
```
npm run test
```

### Lints and fixes files
```
npm run lint
```

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).
