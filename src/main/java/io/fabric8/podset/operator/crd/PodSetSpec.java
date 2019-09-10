package io.fabric8.podset.operator.crd;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;

/*
 */
@JsonDeserialize(
        using = JsonDeserializer.None.class
)
public class PodSetSpec implements KubernetesResource {
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
