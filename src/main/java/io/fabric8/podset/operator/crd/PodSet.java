package io.fabric8.podset.operator.crd;

import io.fabric8.kubernetes.client.CustomResource;

public class PodSet extends CustomResource {
    private PodSetSpec spec;
    private PodSetStatus status;
}
