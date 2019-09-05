package io.fabric8.podset.operator.controller;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import io.fabric8.podset.operator.crd.DoneablePodSet;
import io.fabric8.podset.operator.crd.PodSet;
import io.fabric8.podset.operator.crd.PodSetList;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public class PodSetController {
    private BlockingQueue<String> workqueue;
    private Lister<PodSet> podSetLister;
    private Lister<Pod> podLister;
    private KubernetesClient kubernetesClient;
    private NonNamespaceOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>> podSetClient;

    PodSetController(KubernetesClient kubernetesClient, NonNamespaceOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>> podSetClient, SharedIndexInformer<Pod> podInformer, SharedIndexInformer<PodSet> podSetInformer) {
        this.kubernetesClient = kubernetesClient;
        this.podSetClient = podSetClient;
        this.podLister = new Lister<>(podInformer.getIndexer(), "default");
        this.podSetLister = new Lister<>(podSetInformer.getIndexer(), "default");
    }

    public void create(KubernetesClient kubernetesClient, NonNamespaceOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>> podSetClient, SharedIndexInformer<Pod> podInformer, SharedIndexInformer<PodSet> podSetInformer) {
        podSetInformer.addEventHandler(new ResourceEventHandler<PodSet>() {
            @Override
            public void onAdd(PodSet podSet) {
                enqueuePodSet(podSet);
            }

            @Override
            public void onUpdate(PodSet podSet, PodSet newPodSet) {
                enqueuePodSet(newPodSet);
            }

            @Override
            public void onDelete(PodSet podSet, boolean b) {

            }
        });

        podInformer.addEventHandler(new ResourceEventHandler<Pod>() {
            @Override
            public void onAdd(Pod pod) {

            }

            @Override
            public void onUpdate(Pod pod, Pod t1) {

            }

            @Override
            public void onDelete(Pod pod, boolean b) {

            }
        });
    }

    private void enqueuePodSet(PodSet podSet) {
        String key = Cache.metaNamespaceKeyFunc(podSet);
        if (key != null || !key.isEmpty()) {
            workqueue.add(key);
        }
    }

    private void handlePodObject(Pod pod) {
        OwnerReference ownerReference = getControllerOf(pod);
        if (!ownerReference.getKind().equalsIgnoreCase("PodSet")) {
            return;
        }

    }

    private OwnerReference getControllerOf(Pod pod) {
        List<OwnerReference> ownerReferences = pod.getMetadata().getOwnerReferences();
        for (OwnerReference ownerReference : ownerReferences) {
            if (ownerReference.getController().equals(Boolean.TRUE)) {
                return ownerReference;
            }
        }
        return null;
    }
}
