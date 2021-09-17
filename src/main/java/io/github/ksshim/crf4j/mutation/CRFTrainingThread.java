package io.github.ksshim.crf4j.mutation;

import io.github.ksshim.crf4j.mutation.tagger.EncodeTagger;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Log4j2
@Data
@Builder
public class CRFTrainingThread implements Callable<Integer> {

    private final List<EncodeTagger> taggerList;
    private final int startIndex;
    private final int weightSize;
    private final int nThreads;

    private int zeroOne = 0;
    private int nErrors = 0;
    private double obj = 0.0;
    private double[] expected;

    private void validateAndInit() {
        if(weightSize <= 0) throw new RuntimeException("wSize must be larger than zero. : " + weightSize);

        obj = 0.0;
        nErrors = 0;
        zeroOne = 0;

        if(this.expected == null) this.expected = new double[weightSize];

        Arrays.fill(this.expected, 0.0);
    }

    public void incrementsObj(double addObj) {
        obj += addObj;
    }

    public void incrementsObj(CRFTrainingThread thread) {
        this.incrementsObj(thread.getObj());
    }

    public void incrementsNErrors(CRFTrainingThread thread) {
        nErrors += thread.getNErrors();
    }

    public void incrementsZeroOne(CRFTrainingThread thread) {
        zeroOne += thread.getZeroOne();
    }

    public void incrementsExpected(double addExpected, int index) {
        expected[index] += addExpected;
    }

    public void incrementsExpected(CRFTrainingThread thread) {
        for(int i=0; i<weightSize; i++)
            expected[i] += thread.expected[i];
    }

    public void clearExpected() {
        this.expected = null;
    }

    @Override
    public Integer call() throws Exception {

        validateAndInit();

        try {
            for(int i=startIndex; i<taggerList.size(); i= i+nThreads) {
                EncodeTagger tagger = taggerList.get(i);

                // 1. calculate expectation
                obj += tagger.gradient(expected);
                // 2. evaluation
                int tmpNErrors = tagger.eval();
                tagger.clearNode();

                this.nErrors += tmpNErrors;
                if(tmpNErrors != 0) ++zeroOne;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return nErrors;
    }
}
