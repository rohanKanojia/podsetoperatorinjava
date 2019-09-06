package io.fabric8.podset.operator.crd;

import io.fabric8.kubernetes.api.model.KubernetesResource;

public class PodSetSpec implements KubernetesResource {
    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    private int replicas;
}
