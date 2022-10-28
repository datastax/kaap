package com.datastax.oss.pulsaroperator.reconcilier;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PulsarAutoscalerSpec {

    PulsarAutoscalerConfig autoscaler;
}
