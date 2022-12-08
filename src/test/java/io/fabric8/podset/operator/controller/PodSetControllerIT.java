package io.fabric8.podset.operator.controller;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.podset.operator.model.v1alpha1.PodSet;
import io.fabric8.podset.operator.model.v1alpha1.PodSetSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * This Test requires Operator to be already running in the cluster.
 */
class PodSetControllerIT {
    private KubernetesClient kubernetesClient;
    private MixedOperation<PodSet, KubernetesResourceList<PodSet>, Resource<PodSet>> podSetClient;
    private static final String TEST_NAMESPACE = "default";

    @BeforeEach
    void initPodSetClient() {
        kubernetesClient = new KubernetesClientBuilder().build();
        podSetClient = kubernetesClient.resources(PodSet.class);
    }

    @Test
    void testPodsWithDesiredReplicasCreatedOnPodSetCreate() throws InterruptedException, ExecutionException, TimeoutException {
        // Given
        Pod operatorPod = getOperatorPod();
        waitUntilOperatorIsRunning(operatorPod);
        PodSet podSet = createNewPodSet("test-podset1", 2);

        // When
        podSet = podSetClient.inNamespace(TEST_NAMESPACE).resource(podSet).create();
        waitUntilOperatorLogsPodCreation(operatorPod);

        // Then
        assertEquals(2, getPodSetDependentPodsCount(podSet));
        PodSet podSetFromServer = podSetClient.inNamespace(TEST_NAMESPACE).withName(podSet.getMetadata().getName()).get();
        assertNotNull(podSetFromServer.getStatus());
        assertEquals(2, podSetFromServer.getStatus().getAvailableReplicas());
        kubernetesClient.pods().inNamespace(TEST_NAMESPACE).withLabel("app", "test-podset1").delete();
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

    private void waitUntilOperatorLogsPodCreation(Pod operatorPod) throws ExecutionException, InterruptedException, TimeoutException {
        PodResource podResource = kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
                .resource(operatorPod);
        await(podResource::getLog)
            .apply(l -> l.contains("Created 2 pods for test-podset1 PodSet"))
            .get(30, TimeUnit.SECONDS);
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

    private Function<Predicate<String>, CompletableFuture<String>> await(Supplier<String> supplier) {
        return condition -> CompletableFuture.supplyAsync(() -> {
            String result = null;
            while (!Thread.currentThread().isInterrupted() &&
                   !condition.test(result = supplier.get())) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        });
    }
}
