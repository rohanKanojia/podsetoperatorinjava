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
import io.fabric8.podset.operator.model.v1alpha1.PodSet;
import io.fabric8.podset.operator.model.v1alpha1.PodSetList;
import io.fabric8.podset.operator.model.v1alpha1.PodSetStatus;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PodSetController {
    private final BlockingQueue<String> workqueue;
    private final SharedIndexInformer<PodSet> podSetInformer;
    private final SharedIndexInformer<Pod> podInformer;
    private final Lister<PodSet> podSetLister;
    private final Lister<Pod> podLister;
    private final KubernetesClient kubernetesClient;
    private final MixedOperation<PodSet, PodSetList, Resource<PodSet>> podSetClient;
    public static final Logger logger = Logger.getLogger(PodSetController.class.getName());
    public static final String APP_LABEL = "app";

    public PodSetController(KubernetesClient kubernetesClient, MixedOperation<PodSet, PodSetList, Resource<PodSet>> podSetClient, SharedIndexInformer<Pod> podInformer, SharedIndexInformer<PodSet> podSetInformer, String namespace) {
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
                logger.info("PodSet " + podSet.getMetadata().getName() + " ADDED");
                enqueuePodSet(podSet);
            }

            @Override
            public void onUpdate(PodSet podSet, PodSet newPodSet) {
                logger.info("PodSet " + podSet.getMetadata().getName() + " MODIFIED");
                enqueuePodSet(newPodSet);
            }

            @Override
            public void onDelete(PodSet podSet, boolean b) {
                // Do nothing
            }
        });

        podInformer.addEventHandler(new ResourceEventHandler<Pod>() {
            @Override
            public void onAdd(Pod pod) {
                handlePodObject(pod);
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                if (oldPod.getMetadata().getResourceVersion().equals(newPod.getMetadata().getResourceVersion())) {
                    return;
                }
                handlePodObject(newPod);
            }

            @Override
            public void onDelete(Pod pod, boolean b) {
                // Do nothing
            }
        });
    }

    public void run() {
        logger.log(Level.INFO, "Starting PodSet controller");
        while (!podInformer.hasSynced() || !podSetInformer.hasSynced()) {
            // Wait till Informer syncs
        }

        while (true) {
            try {
                logger.log(Level.INFO, "trying to fetch item from workqueue...");
                if (workqueue.isEmpty()) {
                    logger.log(Level.INFO, "Work Queue is empty");
                }
                String key = workqueue.take();
                Objects.requireNonNull(key, "key can't be null");
                logger.log(Level.INFO, String.format("Got %s", key));
                if (key.isEmpty() || (!key.contains("/"))) {
                    logger.log(Level.WARNING, String.format("invalid resource key: %s", key));
                }

                // Get the PodSet resource's name from key which is in format namespace/name
                String name = key.split("/")[1];
                PodSet podSet = podSetLister.get(key.split("/")[1]);
                if (podSet == null) {
                    logger.log(Level.SEVERE, String.format("PodSet %s in workqueue no longer exists", name));
                    return;
                }
                reconcile(podSet);

            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
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
        logger.info("reconcile() : Found " + pods.size() + " number of Pods owned by PodSet " + podSet.getMetadata().getName());
        if (pods.isEmpty()) {
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

        // Update PodSet status
        updateAvailableReplicasInPodSetStatus(podSet, podSet.getSpec().getReplicas());
    }

    private void createPods(int numberOfPods, PodSet podSet) {
        logger.info(String.format("Creating %d pods for %s PodSet", numberOfPods, podSet.getMetadata().getName()));
        for (int index = 0; index < numberOfPods; index++) {
            Pod pod = createNewPod(podSet);
            pod = kubernetesClient.pods().inNamespace(podSet.getMetadata().getNamespace()).create(pod);
            kubernetesClient.pods().inNamespace(podSet.getMetadata().getNamespace())
                    .withName(pod.getMetadata().getName())
                    .waitUntilCondition(Objects::nonNull, 3, TimeUnit.SECONDS);
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

        logger.log(Level.INFO, String.format("count: %d", podNames.size()));
        return podNames;
    }

    private void enqueuePodSet(PodSet podSet) {
        logger.log(Level.INFO, "enqueuePodSet(" + podSet.getMetadata().getName() + ")");
        String key = Cache.metaNamespaceKeyFunc(podSet);
        logger.log(Level.INFO, String.format("Going to enqueue key %s", key));
        if (key != null && !key.isEmpty()) {
            logger.log(Level.INFO, "Adding item to workqueue");
            workqueue.add(key);
        }
    }

    private void handlePodObject(Pod pod) {
        logger.log(Level.INFO, "handlePodObject(" + pod.getMetadata().getName() + ")");
        OwnerReference ownerReference = getControllerOf(pod);
        Objects.requireNonNull(ownerReference);
        if (!ownerReference.getKind().equalsIgnoreCase("PodSet")) {
            return;
        }
        PodSet podSet = podSetLister.get(ownerReference.getName());
        logger.info("PodSetLister returned " + podSet + " for PodSet");
        if (podSet != null) {
            enqueuePodSet(podSet);
        }
    }

    private void updateAvailableReplicasInPodSetStatus(PodSet podSet, int replicas) {
        PodSetStatus podSetStatus = new PodSetStatus();
        podSetStatus.setAvailableReplicas(replicas);
        podSet.setStatus(podSetStatus);
        podSetClient.inNamespace(podSet.getMetadata().getNamespace()).withName(podSet.getMetadata().getName()).patchStatus(podSet);
    }

    private Pod createNewPod(PodSet podSet) {
        return new PodBuilder()
                .withNewMetadata()
                  .withGenerateName(podSet.getMetadata().getName() + "-pod")
                  .withNamespace(podSet.getMetadata().getNamespace())
                  .withLabels(Collections.singletonMap(APP_LABEL, podSet.getMetadata().getName()))
                  .addNewOwnerReference().withController(true).withKind("PodSet").withApiVersion("demo.k8s.io/v1alpha1").withName(podSet.getMetadata().getName()).withUid(podSet.getMetadata().getUid()).endOwnerReference()
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
