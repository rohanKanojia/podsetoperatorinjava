package io.fabric8.podset.operator.controller;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.podset.operator.model.v1alpha1.PodSet;
import io.fabric8.podset.operator.model.v1alpha1.PodSetList;
import io.fabric8.podset.operator.model.v1alpha1.PodSetSpec;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient
class PodSetControllerTest {
    private KubernetesMockServer server;
    private KubernetesClient client;

    private static final long RESYNC_PERIOD_MILLIS = 10 * 60 * 1000L;

    @Test
    @DisplayName("Should create pods for with respect to a specified PodSet")
    void testReconcile() throws InterruptedException {
        // Given
        String testNamespace = "ns1";
        PodSet testPodSet = getPodSet("example-podset", testNamespace, "0800cff3-9d80-11ea-8973-0e13a02d8ebd");
        setupMockExpectations(testNamespace);

        SharedInformerFactory informerFactory = client.informers();
        MixedOperation<PodSet, PodSetList, Resource<PodSet>> podSetClient = client.resources(PodSet.class, PodSetList.class);
        SharedIndexInformer<Pod> podSharedIndexInformer = informerFactory.sharedIndexInformerFor(Pod.class, RESYNC_PERIOD_MILLIS);
        SharedIndexInformer<PodSet> podSetSharedIndexInformer = informerFactory.sharedIndexInformerFor(PodSet.class, RESYNC_PERIOD_MILLIS);
        PodSetController podSetController = new PodSetController(client, podSetClient, podSharedIndexInformer, podSetSharedIndexInformer, testNamespace);

        // When
        podSetController.reconcile(testPodSet);

        // Then
        RecordedRequest recordedRequest = server.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertTrue(recordedRequest.getBody().readUtf8().contains(testPodSet.getMetadata().getName()));
    }

    private Pod createPodWithName(String name) {
        return new PodBuilder().withNewMetadata().withName(name).endMetadata().build();
    }

    void setupMockExpectations(String testNamespace) {
        setupMockExpectationsForPod(createPodWithName("pod1-clone"), testNamespace);
        setupMockExpectationsForPod(createPodWithName("pod2-clone"), testNamespace);
        setupMockExpectationsForPod(createPodWithName("pod3-clone"), testNamespace);
    }

    void setupMockExpectationsForPod(Pod clonePod, String testNamespace) {
        server.expect().post().withPath("/api/v1/namespaces/" + testNamespace + "/pods")
                .andReturn(HttpURLConnection.HTTP_CREATED, clonePod)
                .once();
        server.expect().get().withPath("/api/v1/namespaces/" + testNamespace + "/pods?fieldSelector=" + Utils.toUrlEncoded("metadata.name=" + clonePod.getMetadata().getName()))
                .andReturn(HttpURLConnection.HTTP_OK, clonePod)
                .once();
        server.expect().get().withPath("/api/v1/namespaces/" + testNamespace + "/pods?fieldSelector=" + Utils.toUrlEncoded("metadata.name=" + clonePod.getMetadata().getName()) + "&watch=true")
                .andUpgradeToWebSocket().open()
                .waitFor(100).andEmit(new WatchEvent(clonePod, "ADDED"))
                .done()
                .once();
    }

    private PodSet getPodSet(String name, String testNamespace, String uid) {
        PodSet podSet = new PodSet();
        PodSetSpec podSetSpec = new PodSetSpec();
        podSetSpec.setReplicas(3);

        podSet.setSpec(podSetSpec);
        podSet.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(testNamespace).withUid(uid).build());
        return podSet;
    }
}
