package com.datastax.oss.pulsaroperator.crd.zookeeper;

import com.datastax.oss.pulsaroperator.crd.GlobalSpec;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZooKeeperFullSpec {
    @Valid
    GlobalSpec global;

    @NotNull
    @Valid
    ZooKeeperSpec zookeeper;
}
