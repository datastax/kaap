package com.datastax.oss.pulsaroperator.reconcilier;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PulsarAutoscalerStatus {

    private PulsarAutoscalerSpec currentSpec;
}
