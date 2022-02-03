package org.apache.lucene.analysis.dynamic;

import org.apache.lucene.analysis.Analyzer;

@FunctionalInterface
public interface AnalyzerProvider {
    Analyzer getAnalyzer(String name);
}
