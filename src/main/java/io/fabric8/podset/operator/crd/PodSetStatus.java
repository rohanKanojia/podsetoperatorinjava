package io.fabric8.podset.operator.crd;

public class PodSetStatus {
    public int getAvailableReplicas() {
        return availableReplicas;
    }

    public void setAvailableReplicas(int availableReplicas) {
        this.availableReplicas = availableReplicas;
    }

    private int availableReplicas;
}
