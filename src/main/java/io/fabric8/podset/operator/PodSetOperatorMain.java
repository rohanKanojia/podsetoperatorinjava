package io.fabric8.podset.operator;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.podset.operator.controller.PodSetController;
import io.fabric8.podset.operator.model.v1alpha1.PodSet;
import io.fabric8.podset.operator.model.v1alpha1.PodSetList;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

/**
 * Main Class for Operator, you can run this sample using this command:
 *
 * mvn exec:java -Dexec.mainClass=io.fabric8.podset.operator.PodSetOperatorMain
 */
public class PodSetOperatorMain {
    public static final Logger logger = Logger.getLogger(PodSetOperatorMain.class.getName());

    public static void main(String[] args) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            String namespace = client.getNamespace();
            if (namespace == null) {
                logger.log(Level.INFO, "No namespace found via config, assuming default.");
                namespace = "default";
            }

            logger.log(Level.INFO, "Using namespace : " + namespace);

            SharedInformerFactory informerFactory = client.informers();

            MixedOperation<PodSet, PodSetList, Resource<PodSet>> podSetClient = client.customResources(PodSet.class, PodSetList.class);
            SharedIndexInformer<Pod> podSharedIndexInformer = informerFactory.sharedIndexInformerFor(Pod.class, 10 * 60 * 1000);
            SharedIndexInformer<PodSet> podSetSharedIndexInformer = informerFactory.sharedIndexInformerForCustomResource(PodSet.class, 10 * 60 * 1000);
            PodSetController podSetController = new PodSetController(client, podSetClient, podSharedIndexInformer, podSetSharedIndexInformer, namespace);

            podSetController.create();
            Future<Void> startedInformersFuture = informerFactory.startAllRegisteredInformers();
            startedInformersFuture.get();
            informerFactory.addSharedInformerEventListener(exception -> logger.log(Level.SEVERE, "Exception occurred, but caught", exception));

            podSetController.run();
        } catch (KubernetesClientException | ExecutionException exception) {
            logger.log(Level.SEVERE, "Kubernetes Client Exception : " + exception.getMessage());
        } catch (InterruptedException interruptedException) {
            logger.log(Level.INFO, "Interrupted: " + interruptedException.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
