package io.github.ksshim.crf4j.mutation;

import io.github.ksshim.crf4j.mutation.constants.Algorithm;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CRFTrainerExample {

    public static void main(String[] args) throws Exception {
        CRFTrainer trainer =
                new CRFTrainer.CRFTrainerBuilder()
                        .algorithm(Algorithm.CRF_L2)
                        .inTemplateFilePath("data/seg/template")
                        .inTrainFilePath("data/seg/train.data")
                        .outModelFilePath("data/seg/out.model")
                        .nThreads(3)
                        .minFrequency(1)
                        .build();

        trainer.train();
    }
}
