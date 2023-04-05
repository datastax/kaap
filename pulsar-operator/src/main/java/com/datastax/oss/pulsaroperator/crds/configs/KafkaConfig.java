package com.datastax.oss.pulsaroperator.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KafkaConfig {

    @JsonPropertyDescription("Allow the cluster to accept Kafka protocol. Default is 'false'.")
    private Boolean enabled;
    @JsonPropertyDescription("Expose the kafka protocol port.")
    private Boolean exposePort;
}
