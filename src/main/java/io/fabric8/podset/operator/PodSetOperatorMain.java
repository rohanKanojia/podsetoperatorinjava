package io.fabric8.podset.operator;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.podset.operator.controller.PodSetController;
import io.fabric8.podset.operator.crd.DoneablePodSet;
import io.fabric8.podset.operator.crd.PodSet;
import io.fabric8.podset.operator.crd.PodSetList;

public class PodSetOperatorMain {
    public static void main(String args[]) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {

            CustomResourceDefinition podSetCustomResourceDefinition = new CustomResourceDefinitionBuilder()
                    .withNewMetadata().withName("podsets.demo.k8s.io").endMetadata()
                    .withNewSpec()
                    .withGroup("demo.k8s.io")
                    .withVersion("v1alpha1")
                    .withNewNames().withKind("PodSet").withPlural("podsets").endNames()
                    .withScope("Namespaced")
                    .endSpec()
                    .build();
            PodSetController podSetController = new PodSetController();

            SharedInformerFactory informerFactory = client.informers();

            MixedOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>> podSetClient = client.customResources(podSetCustomResourceDefinition, PodSet.class, PodSetList.class, DoneablePodSet.class);
            SharedIndexInformer<Pod> podSharedIndexInformer = informerFactory.sharedIndexInformerFor(Pod.class, PodList.class, 10 * 60 * 1000);
            SharedIndexInformer<PodSet> podSetSharedIndexInformer = informerFactory.sharedIndexInformerFor(PodSet.class, PodSetList.class, 10 * 60 * 1000);

            podSetController.create(client, podSetClient, podSharedIndexInformer, podSetSharedIndexInformer);

            informerFactory.startAllRegisteredInformers();
        }
    }
}
