package io.github.ksshim.crf4j.mutation.feature;

import io.github.ksshim.crf4j.mutation.lattice.Node;
import io.github.ksshim.crf4j.mutation.lattice.Path;
import io.github.ksshim.crf4j.mutation.tagger.Tagger;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
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
    protected List<String> biGramTemplates;
    protected String templates;

    protected List<String> tagList;
    protected List<List<Path>> pathList;
    protected List<List<Node>> nodeList;

    protected FeatureIndex() {
        this(1);
    }

    protected FeatureIndex(int threadNum) {
        this.costFactor = 1.0;
        this.threadNum = threadNum;

        this.uniGramTemplates = new LinkedList<>();
        this.biGramTemplates = new LinkedList<>();
        this.tagList = new LinkedList<>();
    }

    protected abstract int getID(String s);

    public String getTagAt(int index) {
        return tagList.get(index);
    }

    //**********************************************************
    // Node cost calculation related
    //**********************************************************
    public void calculateCost(Node node) {
        node.setCost(0.0);
        if (alphaFloat != null) calculateNodeCostWeighted(node);
        else calculateNodeCost(node);
    }

    private void calculateNodeCostWeighted(Node node) {
        float c = 0.0f;
        for(int i=0; node.getFVectorAt(i) != -1; i++) {
            c += alphaFloat[node.getFVectorAt(i) + node.getY()];
        }
        node.setCost(costFactor * c);
    }

    private void calculateNodeCost(Node node) {
        double c = 0.0;
        for(int i=0; node.getFVectorAt(i) != -1; i++) {
            c += alpha[node.getFVectorAt(i) + node.getY()];
        }
        node.setCost(costFactor * c);
    }

    //**********************************************************
    // Path cost calculation related
    //**********************************************************
    public void calculateCost(Path path) {
        path.setCost(0.0);
        if(alphaFloat != null) calculatePathCostWeighted(path);
        else calculatePathCost(path);
    }

    private void calculatePathCostWeighted(Path path) {
        float c = 0.0f;
        for(int i=0; path.getFVectorAt(i) != -1; i++) {
            c += alphaFloat[path.getFVectorAt(i) + (path.getLNodeY() * tagList.size()) + path.getRNodeY()];
        }
        path.setCost(costFactor * c);
    }

    private void calculatePathCost(Path path) {
        float c = 0.0f;
        for(int i=0; path.getFVectorAt(i) != -1; i++) {
            c += alpha[path.getFVectorAt(i) + (path.getLNodeY() * tagList.size()) + path.getRNodeY()];
        }
        path.setCost(costFactor * c);
    }

    //**********************************************************
    // Others
    //**********************************************************
    public String makeTemplates(List<String> niGramTemplates,
                                List<String> biGramTemplates) {

        StringBuilder sb = new StringBuilder();
        // 1. unigram
        for(String temp : uniGramTemplates) {
            sb.append(temp).append('\n');
        }

        // 2. bigram
        for(String temp : biGramTemplates) {
            sb.append(temp).append('\n');
        }

        return sb.toString();
    }

    public String getIndex(String[] idxStr,
                           int cur,
                           Tagger tagger) {
        int row = Integer.parseInt(idxStr[0]);
        int col = Integer.parseInt(idxStr[1]);
        int pos = row + cur;
        if(row < -EOS.length || row > EOS.length ||
                col < 0 || col >= tagger.inputColumnListSize()) return null;

        if(checkMaxXSize) maxXSize = Math.max(maxXSize, col + 1);

        if(pos < 0) {
            return BOS[-pos - 1];
        } else if(pos >= tagger.inputColumnListSize()) {
            return EOS[pos - tagger.inputColumnListSize()];
        } else {
            return tagger.x(pos, col);
        }
    }

    public String applyRule(String str,
                            int cur,
                            Tagger tagger,
                            StringBuilder sb) {
        sb.setLength(0);
        for(String tmp : str.split("%x", -1)) {
            if(StringUtils.isBlank(tmp)) continue;

            if(tmp.startsWith("U") || tmp.startsWith("B")) {
                sb.append(tmp);
                continue;
            }

            String[] tuple = tmp.split("]");
            String[] idx = tuple[0].replace("[", "").split(",");
            String r = getIndex(idx, cur, tagger);
            if(r != null) sb.append(r);
            if(tuple.length > 1) sb.append(tuple[1]);
        }

        return sb.toString();
    }

    private void buildFeatureFromTemplate(List<Integer> featureIdList,
                                          List<String> templates,
                                          int curPos,
                                          Tagger tagger) {

        StringBuilder sb = new StringBuilder();
        for(String template : templates) {
            String feature = applyRule(template, curPos, tagger, sb);
            if(StringUtils.isBlank(feature))
                throw new RuntimeException("Failed to build feature from template ...");

            int featureId = getID(feature);
            // if not exist in dictionary then skip
            if(featureId == -1) continue;

            featureIdList.add(featureId);
        }
    }

    public void buildFeatures(Tagger tagger) {
        tagger.markFeatureIdListIndex();

        // node
        buildFeatures(tagger, 0, uniGramTemplates);
        // path
        buildFeatures(tagger, 1, biGramTemplates);
    }

    private void buildFeatures(Tagger tagger,
                               int startIndex,
                               List<String> templates) {
        for(int cur = startIndex; cur < tagger.inputColumnListSize(); cur++) {
            List<Integer> featureIdList = new LinkedList<>();
            //build feature from template
            buildFeatureFromTemplate(featureIdList, templates, cur, tagger);

            featureIdList.add(-1);
            tagger.addFeatureIdList(featureIdList);
        }
    }

    public void buildNodesAndPaths(Tagger tagger) {

        int tagListSize = tagList.size();
        int featureIdListIndex = tagger.getFeatureIdListIndex();

        // node
        for(int cur=0; cur < tagger.inputColumnListSize(); cur++) {
            List<Integer> featureIdList = tagger.getFeatureIdListAt(featureIdListIndex++);

            for(int i=0; i<tagListSize; i++) {
                Node node = new Node();
                node.setX(cur);
                node.setY(i);
                node.setFeatureVector(featureIdList);
                tagger.setNodeAt(node, cur, i);
            }
        }

        // path
        for(int cur=1; cur < tagger.inputColumnListSize(); cur++) {
            List<Integer> featureIdList = tagger.getFeatureIdListAt(featureIdListIndex++);

            for(int i=0; i<tagListSize; i++) {
                for(int j=0; j<tagListSize; j++) {
                    Path path = new Path();
                    path.add(tagger.getNodeAt(cur-1, i), tagger.getNodeAt(cur, j));
                    path.setFeatureVector(featureIdList);
                }
            }
        }
    }

    public double[] initAlpha() {
        double[] newAlpha = new double[maxId];
        Arrays.fill(newAlpha, 0.0);
        this.alpha = newAlpha;
        return this.alpha;
    }

    public int tagListSize() {
        return tagList.size();
    }

    public void clear() {}
}
