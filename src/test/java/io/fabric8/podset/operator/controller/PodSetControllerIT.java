package io.fabric8.podset.operator.controller;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.readiness.Readiness;
import io.fabric8.kubernetes.client.utils.PodStatusUtil;
import io.fabric8.podset.operator.model.v1alpha1.PodSet;
import io.fabric8.podset.operator.model.v1alpha1.PodSetSpec;
import io.fabric8.junit.jupiter.api.RequireK8sVersionAtLeast;
import io.fabric8.junit.jupiter.api.RequireK8sSupport;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * This Test requires Operator to be already running in the cluster.
 */
@RequireK8sSupport(PodSet.class)
@RequireK8sVersionAtLeast(majorVersion = 1, minorVersion = 16)
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
        assertTrue(getPodSetDependentPodsCount(podSet) >= 2);
        PodSet podSetFromServer = podSetClient.inNamespace(TEST_NAMESPACE).withName(podSet.getMetadata().getName()).get();
        assertNotNull(podSetFromServer.getStatus());
        assertEquals(2, podSetFromServer.getStatus().getAvailableReplicas());
        podSetClient.inNamespace(TEST_NAMESPACE).withName("test-podset1").withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
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
        FilterWatchListDeletable<Pod, PodList, PodResource> podWithLabels = kubernetesClient.pods().inNamespace(TEST_NAMESPACE)
            .withLabel("app", podSet.getMetadata().getName());

        podWithLabels.waitUntilCondition(Readiness::isPodReady, 2, TimeUnit.MINUTES);
        PodList podList = podWithLabels.list();

        int nDependents = 0;
        for (Pod pod : podList.getItems()) {
            boolean isOwnedByPodSet = pod.getMetadata().getOwnerReferences().stream()
                    .anyMatch(o -> o.getController() && o.getUid().equals(podSet.getMetadata().getUid()));
            if (isOwnedByPodSet && Readiness.isPodReady(pod)) {
                System.out.println(pod.getMetadata().getName() + PodStatusUtil.isRunning(pod));
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
            .apply(l ->  {
                System.out.println(l);
                return l.contains("Created 2 pods for test-podset1 PodSet");
            })
            .get(3, TimeUnit.MINUTES);
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
