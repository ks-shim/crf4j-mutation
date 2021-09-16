package io.github.ksshim.crf4j.mutation.feature.serializer;

import io.github.ksshim.crf4j.mutation.constants.CommonConstants;
import io.github.ksshim.crf4j.mutation.feature.DecodeFeatureIndex;
import io.github.ksshim.crf4j.mutation.feature.EncodeFeatureIndex;
import io.github.ksshim.crf4j.mutation.trie.DoubleArrayTrie;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Log4j2
public class FeatureIndexSerializer {

    public final static void read(DecodeFeatureIndex featureIndex,
                                  String inModelPath) throws Exception {

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(inModelPath))) {

            int version = (Integer)ois.readObject();
            featureIndex.setCostFactor((Double)ois.readObject());
            featureIndex.setMaxId((int)ois.readObject());
            featureIndex.setInputColumnSize((int)ois.readObject());
            featureIndex.setTagList((List<String>)ois.readObject());

            featureIndex.setUniGramTemplates((List<String>)ois.readObject());
            featureIndex.setBiGramTemplates((List<String>)ois.readObject());

            DoubleArrayTrie dat = new DoubleArrayTrie();
            int[] datBase = (int[])ois.readObject();
            int[] datCheck = (int[])ois.readObject();
            dat.setBase(datBase);
            dat.setCheck(datCheck);
            dat.setSize(datBase.length);
            featureIndex.setDat(dat);

            featureIndex.setAlpha((double[])ois.readObject());
        }
    }

    public final static void write(EncodeFeatureIndex featureIndex,
                                   String outFilePath) throws Exception {

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outFilePath))) {

            oos.writeObject(CommonConstants.VERSION_OF_MODEL);
            oos.writeObject(featureIndex.getCostFactor());
            oos.writeObject(featureIndex.getMaxId());

            int maxXSize = featureIndex.getMaxXSize();
            int xSize = featureIndex.getInputColumnSize();
            if(maxXSize > 0) xSize = Math.min(xSize, maxXSize);

            oos.writeObject(xSize);
            oos.writeObject(featureIndex.getTagList());
            oos.writeObject(featureIndex.getUniGramTemplates());
            oos.writeObject(featureIndex.getBiGramTemplates());

            List<Pair<String, Integer>> pairList = featureIndex.getDicAsPairList();
            Collections.sort(pairList, new Comparator<Pair<String, Integer>>() {
                @Override
                public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
                    return o1.getKey().compareTo(o2.getKey());
                }
            });

            List<String> keys = new ArrayList<>();
            int[] values = new int[pairList.size()];
            int i = 0;
            for(Pair<String, Integer> pair : pairList) {
                keys.add(pair.getKey());
                values[i++] = pair.getValue();
            }

            DoubleArrayTrie dat = new DoubleArrayTrie();
            log.info("Building trie ...");
            dat.build(keys, null, values, keys.size());
            log.info("Built trie ...");

            oos.writeObject(dat.getBase());
            oos.writeObject(dat.getCheck());
            oos.writeObject(featureIndex.getAlpha());
        }
    }
}
