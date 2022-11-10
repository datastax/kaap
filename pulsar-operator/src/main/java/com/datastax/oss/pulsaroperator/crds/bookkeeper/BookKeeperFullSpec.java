package com.datastax.oss.pulsaroperator.crds.bookkeeper;

import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidSpec;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookKeeperFullSpec implements FullSpecWithDefaults {
    @Valid
    @ValidSpec
    GlobalSpec global;

    @Valid
    @ValidSpec
    BookKeeperSpec bookkeeper;


    @Override
    public GlobalSpec getGlobalSpec() {
        return global;
    }

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (bookkeeper == null) {
            bookkeeper = new BookKeeperSpec();
        }
        bookkeeper.applyDefaults(globalSpec);
    }
}
