package com.datastax.oss.pulsaroperator.autoscaler.bookkeeper;

import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.List;
import lombok.Builder;
import lombok.Data;

public interface BookieAdminClient {

    @Data
    @Builder
    class BookieInfo {
        @Builder.Default
        boolean isWritable = false;
        @Builder.Default
        String rackInfo = "/default-region/default-rack";

        PodResource podResource;
        List<BookieLedgerDiskInfo> ledgerDiskInfos;
        String bookieId;
    }

    @Data
    @Builder
    class BookieLedgerDiskInfo {
        @Builder.Default
        long maxBytes = 0L;
        @Builder.Default
        long usedBytes = 0L;
    }

    List<BookieInfo> collectBookieInfos();

    void setReadOnly(BookieInfo bookieInfo, boolean readonly);

    String recoverAndDeleteCookieInZk(BookieInfo bookieInfo, boolean deleteCookie);

    boolean existsLedger(BookieInfo bookieInfo);

    boolean doesNotHaveUnderReplicatedLedgers();

    void triggerAudit();

    void deleteCookieOnDisk(BookieInfo bookieInfo);
}
