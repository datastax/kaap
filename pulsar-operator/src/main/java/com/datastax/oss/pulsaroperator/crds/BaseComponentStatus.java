package com.datastax.oss.pulsaroperator.crds;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BaseComponentStatus<T> {
    T currentSpec;

    String error;

}
