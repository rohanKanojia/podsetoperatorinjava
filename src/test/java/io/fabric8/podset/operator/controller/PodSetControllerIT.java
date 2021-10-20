package io.fabric8.podset.operator.controller;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.podset.operator.model.v1alpha1.PodSet;
import io.fabric8.podset.operator.model.v1alpha1.PodSetList;
import io.fabric8.podset.operator.model.v1alpha1.PodSetSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * This Test requires Operator to be already running in the cluster.
 */
class PodSetControllerIT {
    private KubernetesClient kubernetesClient;
    private MixedOperation<PodSet, PodSetList, Resource<PodSet>> podSetClient;
    private static final String TEST_NAMESPACE = "default";

    @BeforeEach
    void initPodSetClient() {
        kubernetesClient = new DefaultKubernetesClient();
        podSetClient = kubernetesClient.resources(PodSet.class, PodSetList.class);
    }

    @Test
    void testPodsWithDesiredReplicasCreatedOnPodSetCreate() throws InterruptedException {
        // Given
        Pod operatorPod = getOperatorPod();
        waitUntilOperatorIsRunning(operatorPod);
        PodSet podSet = createNewPodSet("test-podset1", 2);

        // When
        podSet = podSetClient.inNamespace(TEST_NAMESPACE).create(podSet);
        TimeUnit.SECONDS.sleep(30);
        printOperatorLogs(operatorPod);

        // Then
        assertEquals(2, getPodSetDependentPodsCount(podSet));
        PodSet podSetFromServer = podSetClient.inNamespace(TEST_NAMESPACE).withName(podSet.getMetadata().getName()).get();
        assertNotNull(podSetFromServer.getStatus());
        assertEquals(2, podSetFromServer.getStatus().getAvailableReplicas());
    }

    @AfterEach
    void cleanup() {
        podSetClient.inNamespace(TEST_NAMESPACE)
                .withLabel("app", "PodSetControllerIT")
                .withPropagationPolicy(DeletionPropagation.BACKGROUND)
                .delete();
    }

    private int getPodSetDependentPodsCount(PodSet podSet) {
        PodList podList = kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
                .withLabel("app", podSet.getMetadata().getName())
                .list();
        podList.getItems().stream().map(Pod::getMetadata).map(ObjectMeta::getName).forEach(System.out::println);

        int nDependents = 0;
        for (Pod pod : podList.getItems()) {
            boolean isOwnedByPodSet = pod.getMetadata().getOwnerReferences().stream()
                    .anyMatch(o -> o.getController() && o.getUid().equals(podSet.getMetadata().getUid()));
            if (isOwnedByPodSet) {
                nDependents++;
            }
        }
        return nDependents;
    }

    private Pod getOperatorPod() {
        PodList operatorPodList = kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE)
                .withLabel("app", "podset-operator-in-java")
                .list();
        assertEquals(1, operatorPodList.getItems().size());
        return operatorPodList.getItems().get(0);
    }

    private void waitUntilOperatorIsRunning(Pod operatorPod) {
        kubernetesClient.pods()
                .inNamespace(TEST_NAMESPACE)
                .withName(operatorPod.getMetadata().getName())
                .waitUntilReady(2, TimeUnit.MINUTES);
    }

    private void printOperatorLogs(Pod operatorPod) {
        System.out.println(kubernetesClient.pods().inNamespace(TEST_NAMESPACE).withName(operatorPod.getMetadata().getName()).getLog());
    }

    private PodSet createNewPodSet(String name, int replicas) {
        PodSetSpec podSetSpec = new PodSetSpec();
        podSetSpec.setReplicas(replicas);

        PodSet podSet = new PodSet();
        podSet.setMetadata(new ObjectMetaBuilder().withName(name)
                .withLabels(Collections.singletonMap("app", "PodSetControllerIT"))
                .build());
        podSet.setSpec(podSetSpec);
        return podSet;
    }
}
