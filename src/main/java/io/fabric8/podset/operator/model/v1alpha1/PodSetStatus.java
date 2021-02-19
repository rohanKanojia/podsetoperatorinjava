package io.fabric8.podset.operator.model.v1alpha1;

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
