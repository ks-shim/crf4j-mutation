package io.github.ksshim.crf4j.mutation.tagger;

import io.github.ksshim.crf4j.mutation.lattice.Node;
import io.github.ksshim.crf4j.mutation.lattice.Path;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.util.*;

@Log4j2
@Data
public class EncodeTagger extends Tagger {

    private List<Integer> answerTagIndexList;
    private String lastError;

    public EncodeTagger() {
        super();
        this.answerTagIndexList = new LinkedList<>();
    }

    public int eval() {
        int nErrors = 0;
        for(int i=0; i< inputColumnsList.size(); i++) {
            if(answerTagIndexList.get(i).equals(resultList.get(i))) continue;
            nErrors++;
        }
        return nErrors;
    }

    public double gradient(double[] expected) {

        if(inputColumnsList.isEmpty()) return 0.0;

        buildLattice();
        // calculate alpha/beta/z
        forwardBackward();

        // 1. calculate expectation of all nodes
        for(int i=0; i<inputColumnsList.size(); i++) {
            for(int j=0; j<tagListSize; j++) {
                nodesList.get(i)[j].calculateExpectation(expected, z, tagListSize);
            }
        }

        // 2. decrements expectation
        double s = 0.0;
        for(int i=0; i<inputColumnsList.size(); i++) {
            // 2-1. decrements 'expected' by node
            Node node = nodesList.get(i)[answerTagIndexList.get(i)];
            node.decrementsExpected(expected, answerTagIndexList.get(i));
            s += node.getCost();

             // 2-2. decrements 'expected' by paths
            Iterator<Path> iter = node.leftPathIterator();
            while(iter.hasNext()) {
                Path path = iter.next();
                if(path.getLNodeY() != answerTagIndexList.get(path.getLNodeX())) continue;

                path.decrementsExpected(expected, tagListSize);
                s += path.getCost();
                break;
            }
        }

        viterbi();
        return z - s;
    }

    public void shrink() {
        featureIndex.buildFeatures(this);
    }

    public void add(String line) {
        int inputColumnSize = featureIndex.getInputColumnSize();
        String[] cols = line.split("[\t ]", -1);
        if(cols.length < inputColumnSize + 1) {
            throw new RuntimeException(
                    "# x is small : size = " + cols.length + " x-size = " + inputColumnSize);
        }

        String tag = cols[inputColumnSize];
        inputColumnsList.add(cols);
        resultList.add(0);
        answerTagIndexList.add(findTagIndex(tag)); // tag
        nodesList.add(new Node[tagListSize]);
    }

    private int findTagIndex(String label) {
        for(int i=0; i<tagListSize; i++) {
            if(!label.equals(getTagAt(i))) continue;
            return i;
        }

        throw new RuntimeException("Can't find answer ...");
    }

    public void updateFeatureCache(Map<Integer, Integer> oldId2NewIdMap) {
        for(int i=0; i<featureIdListCache.size(); i++) {
            List<Integer> featureCacheItemList = featureIdListCache.get(i);
            List<Integer> newCacheList = new LinkedList<>();

            for(Integer it : featureCacheItemList) {
                if(!oldId2NewIdMap.containsKey(it)) continue;
                newCacheList.add(oldId2NewIdMap.get(it));
            }

            newCacheList.add(-1);
            featureIdListCache.set(i, newCacheList);
        }
    }

    public void clear() {
        super.clear();
        lastError = null;
        answerTagIndexList.clear();
    }
}
