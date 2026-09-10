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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Guards the ordering of bookies used to choose which pods to decommission.
 *
 * <p>A string sort of the pod names puts "bookkeeper-9" after "bookkeeper-12", which made the
 * operator decommission bookkeeper-9 while Kubernetes deleted bookkeeper-12. The set must be
 * ordered numerically so that both agree.
 */
public class BookieOrdinalOrderTest {

    private static final String PREFIX = "pulsar-bookkeeper-";

    private static BookieAdminClient.BookieInfo bookie(int ordinal) {
        return namedBookie(PREFIX + ordinal);
    }

    private static BookieAdminClient.BookieInfo namedBookie(String podName) {
        final Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .endMetadata()
                .build();
        final PodResource podResource = Mockito.mock(PodResource.class);
        Mockito.when(podResource.get()).thenReturn(pod);
        return BookieAdminClient.BookieInfo.builder()
                .podResource(podResource)
                .bookieId(podName + ":3181")
                .build();
    }

    private static List<String> podNames(List<BookieAdminClient.BookieInfo> bookies) {
        return bookies.stream()
                .map(b -> b.getPodResource().get().getMetadata().getName())
                .collect(Collectors.toList());
    }

    /** Builds a shuffled set of 13 bookies, so the test cannot pass on input order alone. */
    private static List<BookieAdminClient.BookieInfo> thirteenBookiesUnordered() {
        final List<BookieAdminClient.BookieInfo> bookies = IntStream.range(0, 13)
                .mapToObj(BookieOrdinalOrderTest::bookie)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(bookies);
        return bookies;
    }

    @Test
    public void testOrdinalParsedFromPodName() {
        Assertions.assertEquals(0, PodExecBookieAdminClient.podOrdinal(PREFIX + "0"));
        Assertions.assertEquals(9, PodExecBookieAdminClient.podOrdinal(PREFIX + "9"));
        Assertions.assertEquals(12, PodExecBookieAdminClient.podOrdinal(PREFIX + "12"));
    }

    /**
     * A name we cannot read must sort first, not last. Bookies are taken from the end of the list, so
     * a pod we cannot identify must never become a candidate for decommission.
     */
    @Test
    public void testUnparseableNameSortsFirstAndDoesNotThrow() {
        Assertions.assertEquals(Integer.MIN_VALUE, PodExecBookieAdminClient.podOrdinal(null));
        Assertions.assertEquals(Integer.MIN_VALUE, PodExecBookieAdminClient.podOrdinal("no-ordinal-here"));
        Assertions.assertEquals(Integer.MIN_VALUE, PodExecBookieAdminClient.podOrdinal("trailing-dash-"));
        Assertions.assertEquals(Integer.MIN_VALUE, PodExecBookieAdminClient.podOrdinal("nodashatall"));
    }

    /** An unrecognised pod is never selected, even when the whole set is scaled down. */
    @Test
    public void testUnparseableNameIsNeverDecommissioned() {
        final List<BookieAdminClient.BookieInfo> bookies = thirteenBookiesUnordered();
        bookies.add(namedBookie("stray-bookie-pod"));
        bookies.sort(PodExecBookieAdminClient.ORDINAL_ORDER);

        Assertions.assertEquals("stray-bookie-pod", podNames(bookies).get(0));

        final RecordingBookieAdminClient adminClient = new RecordingBookieAdminClient();
        BookieDecommissionUtil.decommissionBookies(bookies, 13, adminClient);

        Assertions.assertFalse(adminClient.getDecommissionedPodNames().contains("stray-bookie-pod"));
    }

    /**
     * With 13 replicas a string sort produces 0, 1, 10, 11, 12, 2 ... 9. This asserts the numeric
     * order instead.
     */
    @Test
    public void testDoubleDigitBookiesSortNumerically() {
        final List<BookieAdminClient.BookieInfo> bookies = thirteenBookiesUnordered();
        bookies.sort(PodExecBookieAdminClient.ORDINAL_ORDER);

        final List<String> expected = IntStream.range(0, 13)
                .mapToObj(i -> PREFIX + i)
                .collect(Collectors.toList());
        Assertions.assertEquals(expected, podNames(bookies));
    }

    /**
     * The regression itself: scaling 13 down to 12 must decommission bookkeeper-12, because that is
     * the pod Kubernetes will delete. Before the fix this selected bookkeeper-9.
     */
    @Test
    public void testHighestOrdinalIsDecommissionedFirst() {
        final List<BookieAdminClient.BookieInfo> bookies = thirteenBookiesUnordered();
        bookies.sort(PodExecBookieAdminClient.ORDINAL_ORDER);

        final RecordingBookieAdminClient adminClient = new RecordingBookieAdminClient();
        final int decommissioned = BookieDecommissionUtil.decommissionBookies(bookies, 1, adminClient);

        Assertions.assertEquals(1, decommissioned);
        Assertions.assertEquals(List.of(PREFIX + "12"), adminClient.getDecommissionedPodNames());
    }

    /** Scaling 13 down to 10 must take 12, 11 and 10, in that order. */
    @Test
    public void testMultipleHighestOrdinalsAreDecommissionedInDescendingOrder() {
        final List<BookieAdminClient.BookieInfo> bookies = thirteenBookiesUnordered();
        bookies.sort(PodExecBookieAdminClient.ORDINAL_ORDER);

        final RecordingBookieAdminClient adminClient = new RecordingBookieAdminClient();
        final int decommissioned = BookieDecommissionUtil.decommissionBookies(bookies, 3, adminClient);

        Assertions.assertEquals(3, decommissioned);
        Assertions.assertEquals(
                List.of(PREFIX + "12", PREFIX + "11", PREFIX + "10"),
                adminClient.getDecommissionedPodNames());
    }

    /**
     * If more bookies are requested than the pod list contains, the list is incomplete. Refuse the
     * whole operation instead of decommissioning every visible bookie. The controller retries.
     */
    @Test
    public void testDecommissionRequestLargerThanSetIsRefused() {
        final List<BookieAdminClient.BookieInfo> bookies = thirteenBookiesUnordered();
        bookies.sort(PodExecBookieAdminClient.ORDINAL_ORDER);

        final RecordingBookieAdminClient adminClient = new RecordingBookieAdminClient();
        Assertions.assertThrows(IllegalStateException.class,
                () -> BookieDecommissionUtil.decommissionBookies(bookies, 20, adminClient));
        Assertions.assertTrue(adminClient.getDecommissionedPodNames().isEmpty());
    }

    /** The exact size of the set is allowed, so scaling a whole set to zero still works. */
    @Test
    public void testDecommissionRequestEqualToSetSizeIsAllowed() {
        final List<BookieAdminClient.BookieInfo> bookies = thirteenBookiesUnordered();
        bookies.sort(PodExecBookieAdminClient.ORDINAL_ORDER);

        final RecordingBookieAdminClient adminClient = new RecordingBookieAdminClient();
        final int decommissioned = BookieDecommissionUtil.decommissionBookies(bookies, 13, adminClient);

        Assertions.assertEquals(13, decommissioned);
        Assertions.assertEquals(13, adminClient.getDecommissionedPodNames().size());
    }

    /**
     * A stub that records which pods were taken through decommission and reports every step as
     * successful, so the test asserts on selection rather than on recovery behaviour.
     */
    private static class RecordingBookieAdminClient implements BookieAdminClient {

        private final List<String> cookiesDeleted = new ArrayList<>();

        List<String> getDecommissionedPodNames() {
            return cookiesDeleted;
        }

        @Override
        public List<BookieInfo> collectBookieInfos() {
            return List.of();
        }

        @Override
        public BookieStats collectBookieStats(BookieInfo bookieInfo) {
            return BookieStats.builder().isWritable(true).ledgerDiskInfos(List.of()).build();
        }

        @Override
        public void setReadOnly(BookieInfo bookieInfo, boolean readonly) {
        }

        @Override
        public void recoverAndDeleteCookieInZk(BookieInfo bookieInfo, boolean deleteCookie) {
        }

        @Override
        public boolean existsLedger(BookieInfo bookieInfo) {
            return false;
        }

        @Override
        public boolean doesNotHaveUnderReplicatedLedgers() {
            return true;
        }

        @Override
        public void triggerAudit() {
        }

        @Override
        public void deleteCookieOnDisk(BookieInfo bookieInfo) {
            cookiesDeleted.add(bookieInfo.getPodResource().get().getMetadata().getName());
        }
    }
}
