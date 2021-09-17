package io.github.ksshim.crf4j.mutation.tagger;

import io.github.ksshim.crf4j.mutation.lattice.Node;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
public class DecodeTagger extends Tagger {

    public DecodeTagger() {
        super();
    }

    public void add(String line) {
        int inputColumnSize = featureIndex.getInputColumnSize();
        String[] cols = line.split("[\t ]", -1);
        if(cols.length < inputColumnSize)
            throw new RuntimeException(
                    "# x is small : size = " + cols.length + " x-size = " + inputColumnSize);

        inputColumnsList.add(cols);
        resultList.add(0);
        nodesList.add(new Node[tagListSize]);
    }
}
