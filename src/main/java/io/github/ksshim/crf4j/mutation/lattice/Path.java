package io.github.ksshim.crf4j.mutation.lattice;

import java.util.List;

public class Path {

    private Node rNode;
    private Node lNode;
    private List<Integer> featureVector;
    private double cost;

    public Integer getFVectorAt(int index) {
        return featureVector.get(index);
    }

    public double getLNodeBestCost() {
        return lNode.getBestCost();
    }

    public double getRNodeBestCost() {
        return rNode.getBestCost();
    }

    public int getLNodeX() {
        return lNode.getX();
    }

    public int getRNodeX() {
        return rNode.getX();
    }

    public int getLNodeY() {
        return lNode.getY();
    }

    public int getRNodeY() {
        return rNode.getY();
    }

    public void decrementsExpected(double[] expected, int ySize) {
        for(int i=0; featureVector.get(i) != -1; i++) {
            int index = featureVector.get(i) + (lNode.getY() * ySize) + rNode.getY();
            expected[index]--;
        }
    }

    public void calculateExpectation(double[] expected,
                                     double z,
                                     int size) {
        double c = Math.exp(lNode.getAlpha() + cost + rNode.getBeta() - z);
        for(int i=0; featureVector.get(i) != -1; i++) {
            int index = featureVector.get(i) + (lNode.getY() * size) + rNode.getY();
            expected[index] += c;
        }
    }

    public void add(Node lNode, Node rNode) {
        this.lNode = lNode;
        this.rNode = rNode;
        this.lNode.addRPath(this);
        this.rNode.addLPath(this);
    }

    public void clear() {
        this.rNode = null;
        this.lNode = null;
        this.featureVector = null;
        this.cost = 0.0;
    }
}
