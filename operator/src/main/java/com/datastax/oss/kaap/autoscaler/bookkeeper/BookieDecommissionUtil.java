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
package com.datastax.oss.kaap.autoscaler.bookkeeper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class BookieDecommissionUtil {

    public static int decommissionBookies(List<BookieAdminClient.BookieInfo> allBookies, int numToDecommission,
                                          BookieAdminClient bookieAdminClient) {
        // allBookies must be in ascending StatefulSet ordinal order. Kubernetes removes the highest
        // ordinal first, so the bookies to decommission are the ones at the end of the list.
        List<BookieAdminClient.BookieInfo> bookiesToRemove = new ArrayList<>();
        int sz = allBookies.size();
        if (numToDecommission > sz) {
            // The pod list is smaller than the set we are scaling down, so it is incomplete. Stop
            // here. Continuing would decommission every bookie we can see, including ones that must
            // stay. The controller retries the reconciliation, by which time the list should be
            // complete.
            throw new IllegalStateException(
                    "Asked to decommission %d bookies but only %d are visible. The pod list looks "
                            .formatted(numToDecommission, sz)
                            + "incomplete, so no bookie will be decommissioned.");
        }
        for (int i = sz - 1; i >= sz - numToDecommission; i--) {
            bookiesToRemove.add(allBookies.get(i));
        }
        return decommissionBookies(bookiesToRemove, bookieAdminClient);
    }


    private static int decommissionBookies(List<BookieAdminClient.BookieInfo> bookiesToDecommission,
                                           BookieAdminClient bookieAdminClient) {
        int bookiesToDownscaleCount = bookiesToDecommission.size();
        log.infof("Start decommissioning bookies: %s",
                bookiesToDecommission.stream().map(b -> b.getBookieId()).collect(
                        Collectors.joining(",")));
        Set<BookieAdminClient.BookieInfo> bookiesSetAsReadonly = new HashSet<>(bookiesToDownscaleCount);

        for (BookieAdminClient.BookieInfo bookieInfo : bookiesToDecommission) {
            bookiesSetAsReadonly.add(bookieInfo);
            bookieAdminClient.setReadOnly(bookieInfo, true);
        }

        // wait for bookies to be read-only
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }


        boolean success = true;
        for (BookieAdminClient.BookieInfo bookieInfo : bookiesToDecommission) {
            String bookieName = bookieInfo.getPodResource().get().getMetadata().getName();
            log.infof("Attempting decommission of bookie %s with bookieId = %s",
                    bookieName, bookieInfo.getBookieId());

            if (!runBookieRecovery(bookieInfo, bookieAdminClient)) {
                log.warnf("Can't scale down, failed to recover %s with bookieId = %s",
                        bookieName,
                        bookieInfo.getBookieId());
                success = false;
                break;
            }
        }

        if (success && bookieAdminClient.doesNotHaveUnderReplicatedLedgers()) {
            log.infof("ledgers recovered successfully, proceeding with cookie removal");
        } else {
            log.warnf("Can't scale down, there are under replicated ledgers after recovery");
            success = false;
        }

        if (success) {
            for (BookieAdminClient.BookieInfo bookieInfo : bookiesToDecommission) {
                // todo: I think it is possible to get into a bad state here
                // if the cookie delete passes but connection fails and k8s client returns error.
                // or if the disk cookie deletion fails due to some k8s/network error
                // Cookie on the disk will persist, PVC will be preserved,
                // and on restart the bookie will fail
                if (!deleteCookie(bookieInfo, bookieAdminClient)) {
                    log.warnf("Can't scale down, failed to delete cookie for %s",
                            bookieInfo.getPodResource().get().getMetadata().getName());
                    break;
                }
                bookiesSetAsReadonly.remove(bookieInfo);
            }
        }
        if (bookiesSetAsReadonly.isEmpty()) {
            return bookiesToDownscaleCount;
        }


        int partiallySucceeded = bookiesToDownscaleCount - bookiesSetAsReadonly.size();

        if (partiallySucceeded > 0) {
            log.warnf("Decommission partially succeeded, %d bookies can be removed",
                    partiallySucceeded);
        } else {
            log.warnf("Decommission failed, will retry again later");
        }

        for (BookieAdminClient.BookieInfo bInfo : bookiesSetAsReadonly) {
            bookieAdminClient.setReadOnly(bInfo, false);
        }
        return partiallySucceeded;
    }


    private static boolean runBookieRecovery(BookieAdminClient.BookieInfo bookieInfo,
                                             BookieAdminClient bookieAdminClient) {
        try {
            bookieAdminClient.recoverAndDeleteCookieInZk(bookieInfo, false);
            if (bookieAdminClient.existsLedger(bookieInfo)) {
                log.warnf("Bookie %s still has ledgers assigned to it, will not delete cookie",
                        bookieInfo.getPodResource().get().getMetadata().getName());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.errorf(e, "Error while recovering bookie %s",
                    bookieInfo.getPodResource().get().getMetadata().getName());
            return false;
        }
    }

    private static boolean deleteCookie(BookieAdminClient.BookieInfo bookieInfo, BookieAdminClient bookieAdminClient) {
        try {
            if (bookieAdminClient.existsLedger(bookieInfo)) {
                log.warnf("Bookie %s has ledgers assigned to it, will not delete cookie",
                        bookieInfo.getPodResource().get().getMetadata().getName());
                return false;
            }

            // need to run twice
            bookieAdminClient.recoverAndDeleteCookieInZk(bookieInfo, true);
            bookieAdminClient.deleteCookieOnDisk(bookieInfo);
            return true;
        } catch (Exception e) {
            log.errorf(e, "Error while deleting a cookie for bookie %s",
                    bookieInfo.getPodResource().get().getMetadata().getName());
            return false;
        }
    }

}
