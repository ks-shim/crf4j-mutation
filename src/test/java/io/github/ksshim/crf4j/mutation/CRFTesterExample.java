package io.github.ksshim.crf4j.mutation;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CRFTesterExample {

    public static void main(String[] args) throws Exception {
        CRFTester tester =
                new CRFTester.CRFTesterBuilder()
                        .inTestFilePath("data/seg/test.data")
                        .inModelFilePath("data/seg/out.model")
                        .outFilePath("data/seg/test.out")
                        .build();

        tester.test();
    }
}
