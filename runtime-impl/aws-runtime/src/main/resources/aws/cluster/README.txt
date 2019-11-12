1. Create the cluster:
   terraform init
   terraform plan
   terraform apply

2. Allow kubectl to communicate with the cluster:
   aws eks update-kubeconfig --name jemo-cluster

3. Allow worker nodes to join the cluster:
   terraform output config_map_aws_auth > config-map.yaml
   kubectl apply -f config-map.yaml

4. Instantiate the jemo pods and load balancer service:
   kubectl create -f kubernetes/jemo-statefulset.yaml
   kubectl rollout status statefulset jemo
   kubectl create -f kubernetes/jemo-svc.yaml

5. Find the hostname where jemo runs (you may need to wait 1-2 minutes to see the page loaded):
   kubectl get svc jemo -o=jsonpath='{.status.loadBalancer.ingress[0].hostname}'

To delete everything, run:
   kubectl delete statefulset jemo
   kubectl delete svc jemo
   kubectl delete -n kube-system configmap aws-auth
   terraform destroy

