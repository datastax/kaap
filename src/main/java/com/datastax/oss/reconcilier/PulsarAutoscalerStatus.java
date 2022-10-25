package com.datastax.oss.reconcilier;

import com.datastax.oss.reconcilier.PulsarAutoscalerSpec;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PulsarAutoscalerStatus {

    private PulsarAutoscalerSpec currentSpec;
}
