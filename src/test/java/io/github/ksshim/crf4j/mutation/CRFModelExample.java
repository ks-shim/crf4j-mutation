package io.github.ksshim.crf4j.mutation;

import io.github.ksshim.crf4j.mutation.model.Model;
import io.github.ksshim.crf4j.mutation.model.formatter.InputFormatter;
import io.github.ksshim.crf4j.mutation.tagger.Tagger;
import lombok.extern.log4j.Log4j2;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Log4j2
public class CRFModelExample {

    public static void main(String[] args) throws Exception {

        Model model = new Model("data/spacing/out.model");
        Tagger tagger = model.createTagger();

        InputFormatter inputFormatter = new InputFormatter() {
            @Override
            public void format(String input, Tagger tagger) {
                StringBuilder sb = new StringBuilder();
                for(int i=0; i<input.length(); i++) {
                    sb.setLength(0);
                    sb.append(input.charAt(i));
                    tagger.add(sb.toString());
                }
            }
        };

        List<String> labelList = model.doLabel("나이키신발", tagger, inputFormatter);
        log.info("Result : {}", labelList);
        tagger.clear();

        labelList = model.doLabel("나이키슈즈", tagger, inputFormatter);
        log.info("Result : {}", labelList);
    }
}
