package io.github.ksshim.crf4j.mutation.tagger;

import io.github.ksshim.crf4j.mutation.feature.FeatureIndex;
import io.github.ksshim.crf4j.mutation.lattice.LSE;
import io.github.ksshim.crf4j.mutation.lattice.Node;
import io.github.ksshim.crf4j.mutation.lattice.Path;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.EOFException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Log4j2
@Data
public abstract class Tagger {

    protected int tagListSize;
    protected double cost;
    protected double z;
    protected int threadId;

    protected FeatureIndex featureIndex;
    protected List<String[]> inputColumnsList;
    protected List<Node[]> nodesList;
    protected List<Integer> resultList;

    protected int featureIdListIndex;
    protected List<List<Integer>> featureIdListCache;

    public Tagger() {
        this.inputColumnsList = new ArrayList<>();
        this.nodesList = new ArrayList<>();
        this.resultList = new ArrayList<>();
        this.featureIdListCache = new ArrayList<>();
    }

    public Tagger open(FeatureIndex featureIndex) {
        this.featureIndex = featureIndex;
        this.tagListSize = this.featureIndex.tagListSize();
        return this;
    }

    public Tagger open(FeatureIndex featureIndex, double costFactor) {
        if(costFactor <= 0.0)
            throw new RuntimeException("cost factor must be positive.");

        this.featureIndex = featureIndex;
        this.featureIndex.setCostFactor(costFactor);
        this.tagListSize = this.featureIndex.tagListSize();
        return this;
    }

    //********************************************************************
    //
    // common methods
    //
    //********************************************************************
    public abstract void add(String line);

    public void markFeatureIdListIndex() {
        this.featureIdListIndex = featureIdListCache.size();
    }

    public void addFeatureIdList(List<Integer> featureIdList) {
        this.featureIdListCache.add(featureIdList);
    }

    public List<Integer> getFeatureIdListAt(int index) {
        return this.featureIdListCache.get(index);
    }

    public void parse() {
        featureIndex.buildFeatures(this);

        if(inputColumnsList.isEmpty()) return;

        buildLattice();
        viterbi();
    }

    public void clearNode() {
        if(nodesList == null || nodesList.isEmpty()) return;

        for(Node[] nodes : nodesList) {
            for(int i=0; i<nodes.length; i++) {
                if(nodes[i] == null) continue;

                // free memory
                nodes[i].clear();
                nodes[i] = null;
            }
        }
    }

    public int inputColumnListSize() {
        return inputColumnsList.size();
    }

    public Node getNodeAt(int i, int j) {
        return nodesList.get(i)[j];
    }

    public void setNodeAt(Node n, int i, int j) {
        nodesList.get(i)[j] = n;
    }

    public boolean isEmpty() {
        return inputColumnsList.isEmpty();
    }

    public int getResultAt(int index) {
        return resultList.get(index);
    }

    public String getTagAt(int index) {
        return featureIndex.getTagAt(index);
    }

    public String x(int i, int j) {
        return inputColumnsList.get(i)[j];
    }

    public void clear() {
        inputColumnsList.clear();
        nodesList.clear();
        resultList.clear();
        featureIdListCache.clear();
        z = 0.0;
        cost = 0.0;
    }

    public void read(BufferedReader in) throws Exception {
        clear();

        while(true) {
            String line = in.readLine();
            if(line == null) throw new EOFException();

            line = line.trim();
            if(line.isEmpty()) break;

            add(line);
        }
    }

    //********************************************************************
    //
    // Pick best path related methods
    //
    //********************************************************************
    public void forwardBackward() {

        if(inputColumnsList.isEmpty()) return;

        // forward (alpha)
        for(int i=0; i<inputColumnsList.size(); i++) {
            for(int j=0; j<tagListSize; j++) {
                nodesList.get(i)[j].calcAlpha();
            }
        }

        // backward (beta)
        for(int i=inputColumnsList.size() - 1; i>=0; i--) {
            for(int j=0; j<tagListSize; j++) {
                nodesList.get(i)[j].calcBeta();
            }
        }

        z = 0.0;
        for(int i=0; i<tagListSize; i++) {
            z = LSE.logSumExp(z, nodesList.get(0)[i].getBeta(), i == 0);
        }
    }

    private void calculateBestNodeAndCost(Node node) {
        double bestCost = Double.MIN_VALUE;
        Node best = null;
        for(Path path : node.getLeftPathList()) {
            double cost = path.getLNodeBestCost() + path.getCost() + node.getCost();
            if(cost <= bestCost) continue;

            bestCost = cost;
            best = path.getLNode();
        }

        node.setPrev(best);
        node.setBestCost(best != null ? bestCost : node.getCost());
    }

    public void viterbi() {

        // 1. calculate best node and cost
        for(int i=0; i<inputColumnsList.size(); i++) {
            for(int j=0; j<tagListSize; j++) {
                calculateBestNodeAndCost(nodesList.get(i)[j]);
            }
        }

        // 2. pick the end-node which has the best cost
        Node best = null;
        Node[] endNodes = nodesList.get(inputColumnsList.size() - 1);
        for(int i=0; i<tagListSize; i++) {
            Node endNode = endNodes[i];
            if(best != null && best.getBestCost() >= endNode.getBestCost()) continue;

            best = endNode;
        }

        // 3. store best path(nodes)
        for(Node node = best; node != null; node = node.getPrev()) {
            resultList.set(node.getX(), node.getY());
        }

        cost = -nodesList
                .get(inputColumnsList.size() - 1)[resultList.get(inputColumnsList.size() - 1)]
                .getBestCost();
    }

    //********************************************************************
    //
    // Lattice building related methods
    //
    //********************************************************************
    private void calculateNodeAndPathCost(Node node) {
        featureIndex.calculateCost(node);

        Iterator<Path> iter = node.leftPathIterator();
        while(iter.hasNext()) {
            Path path = iter.next();
            featureIndex.calculateCost(path);
        }
    }

    public void buildLattice() {
        if(inputColumnsList.isEmpty()) return;

        featureIndex.buildNodesAndPaths(this);

        for(int i=0; i<inputColumnsList.size(); i++) {
            for(int j=0; j<tagListSize; j++) {
                calculateNodeAndPathCost(nodesList.get(i)[j]);
            }
        }
    }

    public String asTestResultString() {
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<inputColumnsList.size(); i++) {
            sb.append(StringUtils.join(inputColumnsList.get(i), '\t'));
            sb.append('\t').append(getTagAt(getResultAt(i)));
            sb.append('\n');
        }

        return sb.toString();
    }
}
