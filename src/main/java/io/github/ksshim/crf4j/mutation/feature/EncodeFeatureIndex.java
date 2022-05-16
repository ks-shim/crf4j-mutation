package io.github.ksshim.crf4j.mutation.feature;

import io.github.ksshim.crf4j.mutation.constants.CommonConstants;
import io.github.ksshim.crf4j.mutation.tagger.EncodeTagger;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;

@Log4j2
@Data
public class EncodeFeatureIndex extends FeatureIndex {

    private Map<String, Pair<Integer, Integer>> dic;

    public EncodeFeatureIndex(int n) {
        super(n);
        this.dic = new HashMap<>();
    }

    public List<Pair<String, Integer>> getDicAsPairList() {
        List<Pair<String, Integer>> pairList = new LinkedList<>();
        for(String key : dic.keySet()) {
            pairList.add(Pair.of(key, dic.get(key).getKey()));
        }
        return pairList;
    }

    @Override
    protected int getID(String key) {

        if(!dic.containsKey(key)) {
            dic.put(key, Pair.of(maxId, 1));
            int n = maxId;
            maxId += (key.charAt(0) == CommonConstants.TEMPLATE_UNI_GRAM ?
                    tagList.size() : tagList.size() * tagList.size());
            return n;
        } else {
            Pair<Integer, Integer> pair = dic.get(key);
            int k = pair.getKey();
            int oldVal = pair.getValue();
            dic.put(key, Pair.of(k, oldVal + 1));
            return k;
        }
    }

    private void openTemplate(String inTemplateFilePath) throws Exception {

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(inTemplateFilePath), "UTF-8"))) {

            String line = null;
            while((line = in.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty()) continue;

                char firstChar = line.charAt(0);
                if(firstChar == ' ' || firstChar == '#') continue;

                if(firstChar == CommonConstants.TEMPLATE_UNI_GRAM) {
                    uniGramTemplates.add(line);
                } else if(firstChar == CommonConstants.TEMPLATE_BI_GRAM) {
                    biGramTemplates.add(line);
                } else {
                    log.error("Unknown type : {}", line);
                }
            }

            templates = makeTemplates(uniGramTemplates, biGramTemplates);
        }
    }

    private void openTagSet(String inTrainFilePath) throws Exception {

        int maxSize = 0;
        tagList.clear();

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(inTrainFilePath), "UTF-8"))) {

            String line = null;
            while((line = in.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty()) continue;

                char firstChar = line.charAt(0);
                if(firstChar == '\0' || firstChar == ' ' ||
                        firstChar == '\t') continue;

                String[] cols = line.split("[\t ]", -1);
                if(maxSize == 0) maxSize = cols.length;

                if(maxSize != cols.length) {
                    throw new RuntimeException("inconsistent column size : " + maxSize +
                            " " + cols.length + " " + inTrainFilePath);
                }

                inputColumnSize = cols.length - 1;
                String tag = cols[maxSize - 1];
                if(tagList.indexOf(tag) == -1) tagList.add(tag);
            }

            Collections.sort(tagList);
        }
    }

    public void open(String inTemplateFilePath,
                     String inTrainFilePath) throws Exception {

        checkMaxXSize = true;
        openTemplate(inTemplateFilePath);
        openTagSet(inTrainFilePath);
    }

    public void shrinkFeatureBy(int minFrequency,
                                List<EncodeTagger> taggerList) {

        if(minFrequency <= 1) return;

        int newMaxId = 0;
        Map<Integer, Integer> oldId2NewIdMap = new HashMap<>();
        Map<String, Pair<Integer, Integer>> newDic = new HashMap<>();

        List<String> sortedKeyList = new LinkedList<>(dic.keySet());
        Collections.sort(sortedKeyList);

        // 1. assign new feature ids
        for(String key : sortedKeyList) {
            Pair<Integer, Integer> featureFreq = dic.get(key);
            int freq = featureFreq.getValue();
            if(freq < minFrequency) continue;

            int oldId = featureFreq.getKey();
            oldId2NewIdMap.put(oldId, newMaxId);
            newDic.put(key, Pair.of(newMaxId, freq));
            newMaxId += (key.charAt(0) == CommonConstants.TEMPLATE_UNI_GRAM ? tagList.size() : tagList.size() * tagList.size());
        }

        // 2. update feature cache
        for(EncodeTagger tagger : taggerList) {
            tagger.updateFeatureCache(oldId2NewIdMap);
        }

        // 3. change value and reference
        this.maxId = newMaxId;
        this.dic.clear();
        this.dic = newDic;
    }
}
