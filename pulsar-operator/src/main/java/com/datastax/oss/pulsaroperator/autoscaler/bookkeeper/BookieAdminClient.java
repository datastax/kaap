/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    void recoverAndDeleteCookieInZk(BookieInfo bookieInfo, boolean deleteCookie);

    boolean existsLedger(BookieInfo bookieInfo);

    boolean doesNotHaveUnderReplicatedLedgers();

    void triggerAudit();

    void deleteCookieOnDisk(BookieInfo bookieInfo);
}
