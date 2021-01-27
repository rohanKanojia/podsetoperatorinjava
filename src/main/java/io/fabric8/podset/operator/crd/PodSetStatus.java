package io.fabric8.podset.operator.crd;

import io.fabric8.kubernetes.api.model.KubernetesResource;

public class PodSetStatus {
    public int getAvailableReplicas() {
        return availableReplicas;
    }

    public void setAvailableReplicas(int availableReplicas) {
        this.availableReplicas = availableReplicas;
    }

    @Override
    public String toString() {
        return "PodSetStatus{ availableReplicas=" + availableReplicas + "}";
    }

    private int availableReplicas;
}
