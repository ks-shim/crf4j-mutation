package io.github.ksshim.crf4j.mutation.feature;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
@Data
public abstract class FeatureIndex {

    public final static String[] BOS = {
            "_B-1", "_B-2", "_B-3", "_B-4", "_B-5", "_B-6", "_B-7", "_B-8"
    };

    public final static String[] EOS = {
            "_B+1", "_B+2", "_B+3", "_B+4", "_B+5", "_B+6", "_B+7", "_B+8"
    };

    protected int maxId;
    protected double[] alpha;
    protected float[] alphaFloat;
    protected double costFactor = 1.0;

    protected int inputColumnSize;
    protected boolean checkMaxXSize;
    protected int maxXSize;
    protected int threadNum = 1;

    protected List<String> uniGramTemplates;
    protected List<List<Path>> pathList;
    protected List<List<Node>> nodeList;
}
