1. Create the cluster:
   terraform init
   terraform plan
   terraform apply

2. Allow kubectl to login to the cluster:
   echo "$(terraform output kube_config)" > ~/.kube/config

3. Configure the cluster with pod identity and instantiate the jemo pods:

   a. Create all the needed pods:
      kubectl create -f https://raw.githubusercontent.com/Azure/kubernetes-keyvault-flexvol/master/deployment/kv-flexvol-installer.yaml
      kubectl create secret generic kvcreds --from-literal clientid='JEMO_USER_CLIENT_ID' --from-literal clientsecret='JEMO_USER_CLIENT_SECRET' --type=azure/kv
      kubectl create -f kubernetes/jemo-statefulset.yaml
      kubectl create -f kubernetes/jemo-svc.yaml

   b. Wait until all pods are ready:
      kubectl rollout status statefulset jemo

4. Find the IP where jemo runs:
   kubectl get svc jemo -o=jsonpath='{.status.loadBalancer.ingress[0].ip}'

To delete everything, run:
   kubectl delete statefulset jemo
   kubectl delete svc jemo
   terraform destroy