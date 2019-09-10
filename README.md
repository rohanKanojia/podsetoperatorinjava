# PodSet Operator in Java Using Fabric8 Kubernetes Client

This is a demo operator which implements a simple operator for a custom resource called PodSet which is somewhat equal to ReplicaSet. Here 
is what this resource looks like:
```
apiVersion: demo.k8s.io/v1alpha1
kind: PodSet
metadata:
  name: example-podset
  spec:
    replicas: 5
```

Each PodSet object would have 'x' number of replicas, so this operator just tries to maintain x number of replicas checking whether that number
of pods are running in cluster or not.

## How to Build
```
   mvn clean install
```

## How to Run
```
   mvn exec:java -Dexec.mainClass=io.fabric8.podset.operator.PodSetOperatorMain
```

Make Sure that PodSet Custom Resource Definition is already applied onto the cluster. If not, just apply it using this command:
```
kubectl apply -f src/main/resources/crd.yaml

```
