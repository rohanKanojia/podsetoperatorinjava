package io.fabric8.podset.operator.controller;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import io.fabric8.podset.operator.crd.DoneablePodSet;
import io.fabric8.podset.operator.crd.PodSet;
import io.fabric8.podset.operator.crd.PodSetList;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PodSetController {
    private BlockingQueue<String> workqueue;
    private SharedIndexInformer<PodSet> podSetInformer;
    private SharedIndexInformer<Pod> podInformer;
    private Lister<PodSet> podSetLister;
    private Lister<Pod> podLister;
    private KubernetesClient kubernetesClient;
    private MixedOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>>  podSetClient;
    public static Logger logger = Logger.getLogger(PodSetController.class.getName());
    public static String APP_LABEL = "app";

    public PodSetController(KubernetesClient kubernetesClient, MixedOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>>  podSetClient, SharedIndexInformer<Pod> podInformer, SharedIndexInformer<PodSet> podSetInformer, String namespace) {
        this.kubernetesClient = kubernetesClient;
        this.podSetClient = podSetClient;
        this.podSetLister = new Lister<>(podSetInformer.getIndexer(), namespace);
        this.podSetInformer = podSetInformer;
        this.podLister = new Lister<>(podInformer.getIndexer(), namespace);
        this.podInformer = podInformer;
        this.workqueue = new ArrayBlockingQueue<>(1024);
    }

    public void create() {
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
            public void onDelete(PodSet podSet, boolean b) { }
        });

        podInformer.addEventHandler(new ResourceEventHandler<Pod>() {
            @Override
            public void onAdd(Pod pod) {
                handlePodObject(pod);
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                if (oldPod.getMetadata().getResourceVersion() == newPod.getMetadata().getResourceVersion()) {
                    return;
                }
                handlePodObject(newPod);
            }

            @Override
            public void onDelete(Pod pod, boolean b) { }
        });
    }

    public void run() {
        logger.log(Level.INFO, "Starting PodSet controller");
        while (!podInformer.hasSynced() || !podSetInformer.hasSynced());

        while (true) {
            try {
                logger.log(Level.INFO, "trying to fetch item from workqueue...");
                if (workqueue.isEmpty()) {
                    logger.log(Level.INFO, "Work Queue is empty");
                }
                String key = workqueue.take();
                logger.log(Level.INFO, "Got " + key);
                if (key == null || key.isEmpty() || (!key.contains("/"))) {
                    logger.log(Level.WARNING, "invalid resource key: " + key);
                }

                // Get the PodSet resource's name from key which is in format namespace/name
                String name = key.split("/")[1];
                PodSet podSet = podSetLister.get(key.split("/")[1]);
                if (podSet == null) {
                    logger.log(Level.SEVERE, "PodSet " + name + " in workqueue no longer exists");
                    return;
                }
                reconcile(podSet);

            } catch (InterruptedException interruptedException) {
                logger.log(Level.SEVERE, "controller interrupted..");
            }
        }
    }

    /**
     * Tries to achieve the desired state for podset.
     *
     * @param podSet specified podset
     */
    protected void reconcile(PodSet podSet) {
        List<String> pods = podCountByLabel(APP_LABEL, podSet.getMetadata().getName());
        if (pods == null || pods.size() == 0) {
            createPods(podSet.getSpec().getReplicas(), podSet);
            return;
        }
        int existingPods = pods.size();

        // Compare it with desired state i.e spec.replicas
        // if less then spin up pods
        if (existingPods < podSet.getSpec().getReplicas()) {
            createPods(podSet.getSpec().getReplicas() - existingPods, podSet);
        }

        // If more pods then delete the pods
        int diff = existingPods - podSet.getSpec().getReplicas();
        for (; diff > 0; diff--) {
            String podName = pods.remove(0);
            kubernetesClient.pods().inNamespace(podSet.getMetadata().getNamespace()).withName(podName).delete();
        }
    }

    private void createPods(int numberOfPods, PodSet podSet) {
        for (int index = 0; index < numberOfPods; index++) {
            Pod pod = createNewPod(podSet);
            kubernetesClient.pods().inNamespace(podSet.getMetadata().getNamespace()).create(pod);
        }
    }

    private List<String> podCountByLabel(String label, String podSetName) {
        List<String> podNames = new ArrayList<>();
        List<Pod> pods = podLister.list();

        for (Pod pod : pods) {
            if (pod.getMetadata().getLabels().entrySet().contains(new AbstractMap.SimpleEntry<>(label, podSetName))) {
                if (pod.getStatus().getPhase().equals("Running") || pod.getStatus().getPhase().equals("Pending")) {
                    podNames.add(pod.getMetadata().getName());
                }
            }
        }

        logger.log(Level.INFO, "count: " + podNames.size());
        return podNames;
    }

    private void enqueuePodSet(PodSet podSet) {
        logger.log(Level.INFO, "enqueuePodSet(" + podSet.getMetadata().getName() + ")");
        String key = Cache.metaNamespaceKeyFunc(podSet);
        logger.log(Level.INFO, "Going to enqueue key " + key);
        if (key != null || !key.isEmpty()) {
            logger.log(Level.INFO, "Adding item to workqueue");
            workqueue.add(key);
        }
    }

    private void handlePodObject(Pod pod) {
        logger.log(Level.INFO, "handlePodObject(" + pod.getMetadata().getName() + ")");
        OwnerReference ownerReference = getControllerOf(pod);
        if (!ownerReference.getKind().equalsIgnoreCase("PodSet")) {
            return;
        }
        PodSet podSet = podSetLister.get(ownerReference.getName());
        if (podSet != null) {
            enqueuePodSet(podSet);
        }
    }

    private Pod createNewPod(PodSet podSet) {
        return new PodBuilder()
                .withNewMetadata()
                  .withGenerateName(podSet.getMetadata().getName() + "-pod")
                  .withNamespace(podSet.getMetadata().getNamespace())
                  .withLabels(Collections.singletonMap(APP_LABEL, podSet.getMetadata().getName()))
                  .addNewOwnerReference().withController(true).withKind("PodSet").withApiVersion("demo.k8s.io/v1alpha1").withName(podSet.getMetadata().getName()).withNewUid(podSet.getMetadata().getUid()).endOwnerReference()
                .endMetadata()
                .withNewSpec()
                  .addNewContainer().withName("busybox").withImage("busybox").withCommand("sleep", "3600").endContainer()
                .endSpec()
                .build();
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
