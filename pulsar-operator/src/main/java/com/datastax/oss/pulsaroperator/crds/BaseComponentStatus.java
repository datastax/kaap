package com.datastax.oss.pulsaroperator.crds;

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

    List<Condition> conditions = new ArrayList<>();

    String lastApplied;
}
