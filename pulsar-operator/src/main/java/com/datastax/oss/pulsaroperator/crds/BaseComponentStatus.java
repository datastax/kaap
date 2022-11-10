package com.datastax.oss.pulsaroperator.crds;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BaseComponentStatus {

    public static BaseComponentStatus createReadyStatus() {
        return new BaseComponentStatus(true, null, null);
    }

    public static BaseComponentStatus createErrorStatus(Reason reason, String message) {
        return new BaseComponentStatus(false, reason, message);
    }

    public enum Reason {
        ErrorConfig,
        ErrorUpgrading
    }
    boolean ready;

    Reason reason;

    String message;

}
