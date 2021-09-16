package io.github.ksshim.crf4j.mutation.feature;

import io.github.ksshim.crf4j.mutation.trie.DoubleArrayTrie;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
public class DecodeFeatureIndex extends FeatureIndex {

    private DoubleArrayTrie dat;

    @Override
    protected int getID(String key) {
        return dat.exactMatchSearch(key);
    }
}
