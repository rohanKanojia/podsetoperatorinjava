package io.fabric8.podset.operator.crd;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("demo.fabric8.io")
public class PodSet extends CustomResource<PodSetSpec, PodSetStatus> { }
