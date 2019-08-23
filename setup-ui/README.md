# eclipse-jemo-setup-ui
This repository contains the ui code for the infrastructure setup wizard of Eclipse Jemo.

This UI allows the user to select one of the CSPs currently supported by Eclipse Jemo and navigate him to all the steps needed to build the cloud infrastructure. The user is prompted for Jemo runtime parameters, as well as cloud infrastructure parameters specific to the selected CSP. Eventually, the UI sends REST requests to Jemo which builds all the needed infrastructure including a cluster of nodes running Jemo. This is done by using the managed Kubernetes services offered by CSPs. Default values are offerd by the UI for all parameters, in order to help java developers with limited or no cloud infrastructure knowledge. The two parameters that one still needs to review is the number (defaults to 2) of working nodes in the cluster and their size (defaults to the minimum size offered by the CSP).

This UI runs under https://localhost/jemo/setup/ if Jemo runs locally, otherwise replace localhost with the IP where Jemo runs.

This project is generated with Vue [CLI v3.2.0](https://cli.vuejs.org/) and the [Vue.js v2.5.21](https://vuejs.org/) framework.


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
