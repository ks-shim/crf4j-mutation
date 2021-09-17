package io.github.ksshim.crf4j.mutation;

import io.github.ksshim.crf4j.mutation.constants.Algorithm;
import io.github.ksshim.crf4j.mutation.exception.OptimizationException;
import io.github.ksshim.crf4j.mutation.exception.StopIterationException;
import io.github.ksshim.crf4j.mutation.feature.EncodeFeatureIndex;
import io.github.ksshim.crf4j.mutation.feature.FeatureIndex;
import io.github.ksshim.crf4j.mutation.feature.serializer.FeatureIndexSerializer;
import io.github.ksshim.crf4j.mutation.optimizer.LbfgsOptimizer;
import io.github.ksshim.crf4j.mutation.tagger.EncodeTagger;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
@Builder
public class CRFTrainer {

    @Builder.Default
    private final int minFrequency = 1;
    @Builder.Default
    private final int maxIterations = 10000;
    @Builder.Default
    private final float cost = 1.0f;
    @Builder.Default
    private final float eta = 0.0001f;
    @Builder.Default
    private final Algorithm algorithm = Algorithm.CRF_L1;
    @Builder.Default
    private final int shrinkingSize = 20;
    @Builder.Default
    private final int nThreads = 3;

    private final String inTemplateFilePath;
    private final String inTrainFilePath;
    private final String outModelFilePath;

    public void train() throws Exception {

        // 1. validate parameters
        log.info("Start validating parameters ...");
        validate();
        log.info("End validating parameters ...");

        // 2. create FeatureIndex
        log.info("Start creating feature-index ...");
        EncodeFeatureIndex featureIndex = new EncodeFeatureIndex(nThreads);
        featureIndex.open(inTemplateFilePath, inTrainFilePath);
        log.info("End creating feature-index ...");

        // 3. read train data and create taggers.
        log.info("Start reading train data and creating taggers ...");
        List<EncodeTagger> taggerList = new ArrayList<>(2000000);
        readTrainData(inTrainFilePath, featureIndex, taggerList);
        log.info("End reading train data and creating taggers ...");

        // 4. shrink by min-frequency
        log.info("Start shrinking by min-frequency ...");
        featureIndex.shrinkFeatureBy(minFrequency, taggerList);
        log.info("End shrinking by min-frequency ...");

        // 5. print train info
        log.info("Start printing train info ...");
        printTrainInfo(taggerList, featureIndex);
        log.info("End printing train info ...");

        // 6. start training
        log.info("Start training ...");
        double[] alpha = featureIndex.getAlpha();
        train(taggerList, featureIndex, alpha, algorithm == Algorithm.CRF_L1);
        log.info("End training ...");

        // 7. save model
        log.info("Start saving model ...");
        FeatureIndexSerializer.write(featureIndex, outModelFilePath);
        log.info("End saving model ...");

    }

    private void train(List<EncodeTagger> taggerList,
                       EncodeFeatureIndex featureIndex,
                       double[] alpha,
                       boolean orthant) throws Exception {

        IterationInfo iterationInfo = new IterationInfo();
        LbfgsOptimizer lbfgs = new LbfgsOptimizer();

        // 1. build threads
        List<CRFTrainingThread> threads = buildThreads(alpha.length, taggerList);

        // 2. all x-size
        iterationInfo.taggerListSize = taggerList.size();

        // 3. iterations
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        try {
            for(; iterationInfo.iterationNum < maxIterations; iterationInfo.iterationNum++) {
                try {
                    train(featureIndex, threads, executor, lbfgs, alpha, orthant, iterationInfo);
                } catch (StopIterationException sie) { break; }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static class IterationInfo {
        int taggerListSize = 0;

        double oldObj = 1e+37;
        int converge = 0;
        int iterationNum = 0;
        int all = 0;
        double diff = 0.0;
        int numNonZero = 0;

        void printIterationInfo(CRFTrainingThread firstThread) {
            log.info("");
            log.info("======================================================================");
            log.info("#Iteration = {}", iterationNum);
            log.info(" terr = {}", 1.0 * firstThread.getNErrors() / all);
            log.info(" serr = {}", 1.0 * firstThread.getZeroOne() / taggerListSize);
            log.info(" act = {}", numNonZero);
            log.info(" obj = {}", firstThread.getObj());
            log.info(" diff = {}", diff);
            log.info("");
        }
    }

    private void train(FeatureIndex featureIndex,
                       List<CRFTrainingThread> threads,
                       ExecutorService executor,
                       LbfgsOptimizer lbfgs,
                       double[] alpha,
                       boolean orthant,
                       IterationInfo iterationInfo) throws Exception {

        featureIndex.clear();

        // 1. start and wait until all done
        executor.invokeAll(threads);

        // 2. merge numbers into the first thread
        CRFTrainingThread firstThread = threads.get(0);
        for(int i=1; i<nThreads; i++) {
            CRFTrainingThread tmpThread = threads.get(i);
            firstThread.incrementsObj(tmpThread);
            firstThread.incrementsNErrors(tmpThread);
            firstThread.incrementsZeroOne(tmpThread);
            firstThread.incrementsExpected(tmpThread);
        }

        // 3. do L1/L2 regulation
        iterationInfo.numNonZero = orthant ?
                doL1Regulation(featureIndex.getMaxId(), alpha, firstThread) :
                doL2Regulation(featureIndex.getMaxId(), alpha, firstThread);

        // 4. free some memory
        for(int i=1; i<nThreads; i++) {
            threads.get(i).clearExpected();
        }

        // 5. print iteration info
        iterationInfo.diff = (iterationInfo.iterationNum == 0 ?
                1.0 : Math.abs(iterationInfo.oldObj - firstThread.getObj()) / iterationInfo.oldObj);
        iterationInfo.printIterationInfo(firstThread);

        // 6. update values
        iterationInfo.oldObj = firstThread.getObj();
        iterationInfo.converge = iterationInfo.diff < eta ? iterationInfo.converge + 1 : 0;
        if(iterationInfo.iterationNum > maxIterations || iterationInfo.converge == 3)
            throw new StopIterationException();

        if(lbfgs.optimize(featureIndex.getMaxId(), alpha, firstThread.getObj(),
                firstThread.getExpected(), orthant, cost) <= 0) throw new OptimizationException();
    }

    private int doL1Regulation(int size,
                               double[] alpha,
                               CRFTrainingThread firstThread) {
        int numNonZero = 0;
        for(int i=0; i<size; i++) {
            firstThread.incrementsObj(Math.abs(alpha[i] / cost));
            if(alpha[i] == 0.0) continue;
            numNonZero++;
        }
        return numNonZero;
    }

    private int doL2Regulation(int size,
                               double[] alpha,
                               CRFTrainingThread firstThread) {

        for(int i=0; i<size; i++) {
            firstThread.incrementsObj((alpha[i] * alpha[i] / (2.0 * cost)));
            firstThread.incrementsExpected(alpha[i] /cost, i);
        }
        return size;
    }

    private List<CRFTrainingThread> buildThreads(int alphaLength,
                                                 List<EncodeTagger> taggerList) {
        List<CRFTrainingThread> threads = new ArrayList<>();

        for(int i=0; i<nThreads; i++) {
            threads.add(
                    new CRFTrainingThread.CRFTrainingThreadBuilder()
                            .weightSize(alphaLength)
                            .startIndex(i)
                            .nThreads(nThreads)
                            .taggerList(taggerList)
                            .build()
            );
        }
        return threads;
    }

    private void readTrainData(String inTrainFilePath,
                               EncodeFeatureIndex featureIndex,
                               List<EncodeTagger> taggerList) throws Exception {

        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                        new FileInputStream(inTrainFilePath), "UTF-8"))) {

            int lineNo = 0;
            while(true) {
                EncodeTagger tagger = new EncodeTagger();
                tagger.open(featureIndex);
                try {
                    tagger.read(in);
                } catch (EOFException eof) {
                    if(tagger.isEmpty()) break;
                }

                tagger.shrink();
                tagger.setThreadId(lineNo % nThreads);
                taggerList.add(tagger);

                if(++lineNo % 100 == 0) log.info("{} ...", lineNo);
            }
        }
    }

    private void validate() {
        if(eta <= 0) throw new RuntimeException("eta must be > 0.0");
        if(cost < 0.0) throw new RuntimeException("cost must be >= 0.0");
        if(shrinkingSize < 1) throw new RuntimeException("shrinkingSize must be >= 1");
        if(nThreads <= 0) throw new RuntimeException("thread must be > 0");
    }

    private void printTrainInfo(List<EncodeTagger> taggerList,
                                FeatureIndex featureIndex) {

        log.info("");
        log.info("====================================================================");
        log.info("Number of sentences : {}", taggerList.size());
        log.info("Number of features : {}", featureIndex.getMaxId());
        log.info("Number of thread(s) : {}", nThreads);
        log.info("Min frequency : {}", minFrequency);
        log.info("Eta : {}", eta);
        log.info("Cost : {}", cost);
        log.info("Shrinking size : {}", shrinkingSize);
        log.info("");
    }
}
