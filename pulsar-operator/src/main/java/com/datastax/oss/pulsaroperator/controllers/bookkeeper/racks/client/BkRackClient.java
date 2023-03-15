package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client;

import java.util.Map;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface BkRackClient extends AutoCloseable {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class BookieRackInfo {
        String rack;
        String hostname;
    }

    class BookiesRackConfiguration extends TreeMap<String, Map<String, BookieRackInfo>> {}

    interface BookiesRackOp {
        BookiesRackConfiguration get();
        void update(BookiesRackConfiguration newConfig);
    }


    BookiesRackOp newBookiesRackOp();
}
