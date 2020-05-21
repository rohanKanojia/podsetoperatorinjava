package io.fabric8.podset.operator;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerEventListener;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.podset.operator.controller.PodSetController;
import io.fabric8.podset.operator.crd.DoneablePodSet;
import io.fabric8.podset.operator.crd.PodSet;
import io.fabric8.podset.operator.crd.PodSetList;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main Class for Operator, you can run this sample using this command:
 *
 * mvn exec:java -Dexec.mainClass=io.fabric8.podset.operator.PodSetOperatorMain
 */
public class PodSetOperatorMain {
    public static Logger logger = Logger.getLogger(PodSetOperatorMain.class.getName());

    public static void main(String args[]) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            String namespace = client.getNamespace();
            if (namespace == null) {
                logger.log(Level.INFO, "No namespace found via config, assuming default.");
                namespace = "default";
            }

            logger.log(Level.INFO, "Using namespace : " + namespace);
            CustomResourceDefinition podSetCustomResourceDefinition = new CustomResourceDefinitionBuilder()
                    .withNewMetadata().withName("podsets.demo.k8s.io").endMetadata()
                    .withNewSpec()
                    .withGroup("demo.k8s.io")
                    .withVersion("v1alpha1")
                    .withNewNames().withKind("PodSet").withPlural("podsets").endNames()
                    .withScope("Namespaced")
                    .endSpec()
                    .build();
            CustomResourceDefinitionContext podSetCustomResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                    .withVersion("v1alpha1")
                    .withScope("Namespaced")
                    .withGroup("demo.k8s.io")
                    .withPlural("podsets")
                    .build();

            SharedInformerFactory informerFactory = client.informers();

            MixedOperation<PodSet, PodSetList, DoneablePodSet, Resource<PodSet, DoneablePodSet>> podSetClient = client.customResources(podSetCustomResourceDefinition, PodSet.class, PodSetList.class, DoneablePodSet.class);
            SharedIndexInformer<Pod> podSharedIndexInformer = informerFactory.sharedIndexInformerFor(Pod.class, PodList.class, 10 * 60 * 1000);
            SharedIndexInformer<PodSet> podSetSharedIndexInformer = informerFactory.sharedIndexInformerForCustomResource(podSetCustomResourceDefinitionContext, PodSet.class, PodSetList.class, 10 * 60 * 1000);
            PodSetController podSetController = new PodSetController(client, podSetClient, podSharedIndexInformer, podSetSharedIndexInformer, namespace);

            podSetController.create();
            informerFactory.startAllRegisteredInformers();
            informerFactory.addSharedInformerEventListener(exception -> logger.log(Level.SEVERE, "Exception occurred, but caught", exception));

            podSetController.run();
        } catch (KubernetesClientException exception) {
            logger.log(Level.SEVERE, "Kubernetes Client Exception : {}", exception.getMessage());
        }
    }
}
