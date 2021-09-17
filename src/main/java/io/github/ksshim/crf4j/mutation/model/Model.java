package io.github.ksshim.crf4j.mutation.model;

import io.github.ksshim.crf4j.mutation.feature.DecodeFeatureIndex;
import io.github.ksshim.crf4j.mutation.feature.serializer.FeatureIndexSerializer;
import io.github.ksshim.crf4j.mutation.model.formatter.InputFormatter;
import io.github.ksshim.crf4j.mutation.tagger.DecodeTagger;
import io.github.ksshim.crf4j.mutation.tagger.Tagger;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Log4j2
public class Model {

    private final DecodeFeatureIndex featureIndex;

    public Model(String inModelFilePath) {
        this.featureIndex = createDecodeFeatureIndex(inModelFilePath);
    }

    private DecodeFeatureIndex createDecodeFeatureIndex(String inModelFilePath) {
        DecodeFeatureIndex featureIndex = new DecodeFeatureIndex();
        try {
            FeatureIndexSerializer.read(featureIndex, inModelFilePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return featureIndex;
    }

    public DecodeTagger createTagger() {
        return createTagger(1.0);
    }

    public DecodeTagger createTagger(double costFactor) {
        DecodeTagger tagger = new DecodeTagger();
        return (DecodeTagger) tagger.open(featureIndex, costFactor);
    }

    public List<String> doLabel(String input,
                                Tagger tagger,
                                InputFormatter inputFormatter) {
        if(StringUtils.isBlank(input)) return Collections.emptyList();

        inputFormatter.format(input, tagger);
        tagger.parse();

        List<String> labelList = new LinkedList<>();
        for(int i=0; i<tagger.inputColumnListSize(); i++) {
            labelList.add(tagger.getTagAt(tagger.getResultAt(i)));
        }
        return labelList;
    }
}
