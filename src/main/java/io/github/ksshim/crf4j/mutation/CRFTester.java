package io.github.ksshim.crf4j.mutation;

import io.github.ksshim.crf4j.mutation.model.Model;
import io.github.ksshim.crf4j.mutation.tagger.Tagger;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;

import java.io.*;

@Log4j2
@Builder
public class CRFTester {

    @Builder.Default
    private final double costFactor = 1.0;

    private final String inModelFilePath;
    private final String inTestFilePath;
    private final String outFilePath;

    public void test() throws Exception {

        // 1. create tagger and featureIndex
        log.info("Start creating model and tagger ...");
        Model model = new Model(inModelFilePath);
        Tagger tagger = model.createTagger(costFactor);
        log.info("End creating model and tagger ...");

        // 2. read test data and label it
        log.info("Start reading test data and labeling it ...");
        readAndLabelData(tagger);
        log.info("End reading test data and labeling it ...");

        // 3. calculate score
        log.info("Start calculating score and printing it ...");
        printAndCalculateScore();
        log.info("End calculating score and printing it ...");
    }

    private void readAndLabelData(Tagger tagger) throws Exception {

        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(inTestFilePath), "UTF-8"));
             BufferedWriter out = new BufferedWriter(new FileWriter(outFilePath))) {

            while(true) {
                try {
                    tagger.read(in);
                } catch (EOFException eof) {
                    if(tagger.isEmpty()) break;
                }

                tagger.parse();

                out.write(tagger.asTestResultString());
                out.newLine();
            }
        }
    }

    private void printAndCalculateScore() throws Exception {

        int total = 0;
        int correct = 0;

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(outFilePath)))) {

            String line = null;
            while((line = in.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty() || line.charAt(0) == '#') continue;

                String[] columns = line.split("[\t ]", -1);
                int columnLen = columns.length;
                assert columnLen > 2;

                if(columns[columnLen - 1].equals(columns[columnLen - 2])) correct++;

                total++;
            }
        }

        log.info("Score : {}", (double)correct / total);
    }
}
