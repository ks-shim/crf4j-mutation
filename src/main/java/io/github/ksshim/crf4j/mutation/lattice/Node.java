package io.github.ksshim.crf4j.mutation.lattice;

import lombok.Data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class Node {

    public final static double LOG2 = 0.69314718055;

    private int x;
    private int y;
    private double alpha;
    private double beta;
    private double cost;
    private double bestCost = -1e37;

    private Node prev;
    private List<Integer> featureVector;
    private List<Path> leftPathList;
    private List<Path> rightPathList;

    public Node() {
        this.leftPathList = new ArrayList<>();
        this.rightPathList = new ArrayList<>();
    }

    public Iterator<Path> leftPathIterator() {
        return leftPathList.iterator();
    }

    public Iterator<Path> rightPathIterator() {
        return rightPathList.iterator();
    }

    public void addRPath(Path path) {
        rightPathList.add(path);
    }

    public void addLPath(Path path) {
        leftPathList.add(path);
    }

    public Integer getFVectorAt(int index) {
        return featureVector.get(index);
    }

    public void decrementsExpected(double[] expected, int index) {
        for (int i=0; featureVector.get(i) != -1; i++) {
            int idx = featureVector.get(i) + index;
            expected[idx]--;
        }
    }

    public void calcAlpha() {
        this.alpha = 0.0;
        for(Path p : leftPathList) {
            this.alpha = LSE.logSumExp(alpha, p.get)
        }
    }

    public void clear() {
        this.x = 0;
        this.y = 0;
        this.alpha = 0;
        this.beta = 0;
        this.cost = 0;

        this.prev = null;
        this.featureVector = null;

        this.leftPathList.clear();
        this.rightPathList.clear();
    }
}
