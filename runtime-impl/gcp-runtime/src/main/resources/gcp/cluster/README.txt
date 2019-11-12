1. Create the cluster:
   terraform init
   terraform plan
   terraform apply

2. Allow kubectl to communicate with the cluster:
   gcloud container clusters get-credentials jemo-cluster

3. Instantiate the jemo pods and load balancer service:
   kubectl create -f kubernetes/credentials.yaml
   kubectl create -f kubernetes/jemo-statefulset.yaml
   kubectl rollout status statefulset jemo
   kubectl create -f kubernetes/jemo-svc.yaml

5. Find the hostname where jemo runs (you may need to wait 1-2 minutes to see the page loaded):
   kubectl get svc jemo -o=jsonpath='{.status.loadBalancer.ingress[0].ip}' -w

To delete everything, run:
   kubectl delete secret jemo-user-cred
   kubectl delete statefulset jemo
   kubectl delete svc jemo
   terraform destroy

