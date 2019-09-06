package io.fabric8.podset.operator.crd;

import io.fabric8.kubernetes.client.CustomResource;

public class PodSet extends CustomResource {
    public PodSetSpec getSpec() {
        return spec;
    }

    public void setSpec(PodSetSpec spec) {
        this.spec = spec;
    }

    public PodSetStatus getStatus() {
        return status;
    }

    public void setStatus(PodSetStatus status) {
        this.status = status;
    }

    private PodSetSpec spec;
    private PodSetStatus status;
}
