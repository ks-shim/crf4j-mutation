package io.github.ksshim.crf4j.mutation.model.formatter;

import io.github.ksshim.crf4j.mutation.tagger.Tagger;

public interface InputFormatter {

    void format(String input, Tagger tagger);
}
