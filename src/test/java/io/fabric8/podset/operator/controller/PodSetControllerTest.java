package io.fabric8.podset.operator.controller;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
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
        server.expect().post().withPath("/api/v1/namespaces/" + testNamespace + "/pods")
                .andReturn(HttpURLConnection.HTTP_CREATED, new PodBuilder().withNewMetadata().withName("pod1-clone").endMetadata().build())
                .times(testPodSet.getSpec().getReplicas());

        SharedInformerFactory informerFactory = client.informers();
        MixedOperation<PodSet, PodSetList, Resource<PodSet>> podSetClient = client.customResources(PodSet.class, PodSetList.class);
        SharedIndexInformer<Pod> podSharedIndexInformer = informerFactory.sharedIndexInformerFor(Pod.class, RESYNC_PERIOD_MILLIS);
        SharedIndexInformer<PodSet> podSetSharedIndexInformer = informerFactory.sharedIndexInformerForCustomResource(PodSet.class, RESYNC_PERIOD_MILLIS);
        PodSetController podSetController = new PodSetController(client, podSetClient, podSharedIndexInformer, podSetSharedIndexInformer, testNamespace);

        // When
        podSetController.reconcile(testPodSet);

        // Then
        RecordedRequest recordedRequest = server.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertTrue(recordedRequest.getBody().readUtf8().contains(testPodSet.getMetadata().getName()));
    }

    private PodSet getPodSet(String name, String testNamespace, String uid) {
        PodSet podSet = new PodSet();
        PodSetSpec podSetSpec = new PodSetSpec();
        podSetSpec.setReplicas(5);

        podSet.setSpec(podSetSpec);
        podSet.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(testNamespace).withUid(uid).build());
        return podSet;
    }
}
