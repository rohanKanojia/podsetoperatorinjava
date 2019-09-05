package io.fabric8.podset.operator.crd;

import io.fabric8.kubernetes.api.model.KubernetesResource;

public class PodSetSpec implements KubernetesResource {
    private int replicas;
}
