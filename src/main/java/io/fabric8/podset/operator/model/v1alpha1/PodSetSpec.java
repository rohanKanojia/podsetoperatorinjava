package io.fabric8.podset.operator.model.v1alpha1;

public class PodSetSpec {
    public int getReplicas() {
        return replicas;
    }

    @Override
    public String toString() {
        return "PodSetSpec{replicas=" + replicas + "}";
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    private int replicas;
}
