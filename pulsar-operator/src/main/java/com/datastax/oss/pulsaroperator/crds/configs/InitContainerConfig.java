package com.datastax.oss.pulsaroperator.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitContainerConfig {
    @JsonPropertyDescription("The image used to run the container.")
    private String image;
    @JsonPropertyDescription("The image pull policy used for the container.")
    private String imagePullPolicy;
    @JsonPropertyDescription("The command used for the container.")
    private List<String> command;
    @JsonPropertyDescription("The command args used for the container.")
    private List<String> args;
    @JsonPropertyDescription("The container path where the emptyDir volume is mounted.")
    private String emptyDirPath;
}
