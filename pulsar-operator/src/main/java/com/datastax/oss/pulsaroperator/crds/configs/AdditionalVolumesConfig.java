package com.datastax.oss.pulsaroperator.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdditionalVolumesConfig {

    @JsonPropertyDescription("Additional volumes to be mounted to the pod")
    List<Volume> volumes;
    @JsonPropertyDescription("Mount points for the additional volumes")
    List<VolumeMount> mounts;
}
