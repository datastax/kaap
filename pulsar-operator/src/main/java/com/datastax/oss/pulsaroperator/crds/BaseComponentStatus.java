package com.datastax.oss.pulsaroperator.crds;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Condition;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BaseComponentStatus {
    @JsonPropertyDescription("Conditions:\n"
            + " 1. Condition " + CRDConstants.CONDITIONS_TYPE_READY + ": possible status are True or False. If False, the reason contains the error message.")
    List<Condition> conditions = new ArrayList<>();

    @JsonPropertyDescription("Last spec applied.")
    String lastApplied;
}
