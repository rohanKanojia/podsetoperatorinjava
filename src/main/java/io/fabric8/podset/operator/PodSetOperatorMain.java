package io.fabric8.podset.operator;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.podset.operator.controller.PodSetController;
import io.fabric8.podset.operator.model.v1alpha1.PodSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

/**
 * Main Class for Operator, you can run this sample using this command:
 * <p>
 * mvn exec:java -Dexec.mainClass=io.fabric8.podset.operator.PodSetOperatorMain
 */
public class PodSetOperatorMain {
    public static final Logger logger = LoggerFactory.getLogger(PodSetOperatorMain.class.getSimpleName());

    public static void main(String[] args) {
        SharedInformerFactory informerFactory = null;
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            String namespace = client.getNamespace();
            if (namespace == null) {
                logger.info("No namespace found via config, assuming default.");
                namespace = "default";
            }

            logger.info("Using namespace : {}", namespace);

            informerFactory = client.informers();

            MixedOperation<PodSet, KubernetesResourceList<PodSet>, Resource<PodSet>> podSetClient = client.resources(PodSet.class);
            SharedIndexInformer<Pod> podSharedIndexInformer = informerFactory.sharedIndexInformerFor(Pod.class, 10 * 60 * 1000L);
            SharedIndexInformer<PodSet> podSetSharedIndexInformer = informerFactory.sharedIndexInformerFor(PodSet.class, 10 * 60 * 1000L);
            PodSetController podSetController = new PodSetController(client, podSetClient, podSharedIndexInformer, podSetSharedIndexInformer, namespace);

            Future<Void> startedInformersFuture = informerFactory.startAllRegisteredInformers();
            startedInformersFuture.get();
            informerFactory.addSharedInformerEventListener(exception -> logger.error("Exception occurred, but caught", exception));

            podSetController.run();
        } catch (KubernetesClientException | ExecutionException exception) {
            logger.error("Kubernetes Client Exception : ", exception);
        } catch (InterruptedException interruptedException) {
            logger.error("Interrupted: ", interruptedException);
            Thread.currentThread().interrupt();
        } finally {
            if (informerFactory != null) {
                informerFactory.stopAllRegisteredInformers();
            }
        }
    }
}
