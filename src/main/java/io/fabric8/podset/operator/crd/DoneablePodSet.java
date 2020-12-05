package io.fabric8.podset.operator.crd;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class DoneablePodSet extends CustomResourceDoneable<PodSet> {
    public DoneablePodSet(PodSet resource, Function function) { super(resource, function); }
}
