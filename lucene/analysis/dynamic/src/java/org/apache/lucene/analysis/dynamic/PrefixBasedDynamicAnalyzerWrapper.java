package org.apache.lucene.analysis.dynamic;

import org.apache.lucene.analysis.Analyzer;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PrefixBasedDynamicAnalyzerWrapper extends Analyzer {

    private final LazyObj<PrefixBasedDynamicAnalyzer> analyzer;

    public PrefixBasedDynamicAnalyzerWrapper(String prefix, int gap, List<String> baseAnalyzers, Map<String, List<String>> includeAnalyzersForPrefixMap,
                                             Map<String, List<String>> excludeAnalyzersForPrefixMap, AnalyzerProvider analyzerProvider) {

        if (baseAnalyzers == null || baseAnalyzers.isEmpty()) {
            throw new IllegalArgumentException("Analyzer of type prefix based, must have a \"base_analyzers\" list property");
        }
        if (analyzerProvider == null) {
            throw new IllegalArgumentException("Analyzer lookup not provided");
        }
        this.analyzer = new LazyObj<>(() -> {
            final List<Analyzer> defaultAnalyzers = baseAnalyzers.stream()
                    .map(name -> getNonNullAnalyzer(analyzerProvider, name))
                    .collect(Collectors.toList());
            final Map<String, Set<String>> prefixVsAnalyzerNamesMap =
                    createPrefixAnalyzerNamesMap(baseAnalyzers, includeAnalyzersForPrefixMap, excludeAnalyzersForPrefixMap);
            return new PrefixBasedDynamicAnalyzer(prefix, gap, defaultAnalyzers,
                    createPrefixVsAnalyzersMap(analyzerProvider, prefixVsAnalyzerNamesMap));
        });
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return this.analyzer.getOrCreate().createComponents(fieldName);
    }

    @Override
    public void close() {
        if (analyzer.getIfPresent() != null) {
            this.analyzer.getIfPresent().close();
        }
        super.close();
    }

    private Map<String, List<Analyzer>>
    createPrefixVsAnalyzersMap(AnalyzerProvider analyzerProvider, Map<String, Set<String>> prefixVsAnalyzerNamesMap) {
        Map<String, List<Analyzer>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : prefixVsAnalyzerNamesMap.entrySet()) {
            List<Analyzer> analyzers = new ArrayList<>();
            for (String name : entry.getValue()) {
                analyzers.add(getNonNullAnalyzer(analyzerProvider, name));
            }
            result.put(entry.getKey(), analyzers);
        }
        return result;
    }

    private Map<String, Set<String>> createPrefixAnalyzerNamesMap(List<String> baseAnalyzers,
                                                                  Map<String, List<String>> includeAnalyzersForPrefixMap,
                                                                  Map<String, List<String>> excludeAnalyzersForPrefixMap) {
        final Map<String, Set<String>> prefixVsAnalyzerNamesMap = new HashMap<>();

        if (includeAnalyzersForPrefixMap != null) {
            for (Map.Entry<String, List<String>> entry : includeAnalyzersForPrefixMap.entrySet()) {
                Set<String> analyzers = new LinkedHashSet<>(baseAnalyzers);
                analyzers.addAll(entry.getValue());
                prefixVsAnalyzerNamesMap.put(entry.getKey(), analyzers);
            }
        }

        if (excludeAnalyzersForPrefixMap != null) {
            for (Map.Entry<String, List<String>> entry : excludeAnalyzersForPrefixMap.entrySet()) {
                if (prefixVsAnalyzerNamesMap.containsKey(entry.getKey())) {
                    Set<String> analyzerNames = prefixVsAnalyzerNamesMap.get(entry.getKey());
                    for (String name : entry.getValue()) {
                        analyzerNames.remove(name);
                    }
                } else {
                    Set<String> analyzerNames = new LinkedHashSet<>(baseAnalyzers);
                    boolean removed = false;
                    for (String name : entry.getValue()) {
                        removed |= analyzerNames.remove(name);
                    }
                    if (removed) {
                        prefixVsAnalyzerNamesMap.put(entry.getKey(), analyzerNames);
                    }
                }
            }
        }
        return prefixVsAnalyzerNamesMap;
    }

    public Analyzer getNonNullAnalyzer(AnalyzerProvider analyzerProvider, String name) {
        Analyzer analyzer = analyzerProvider.getAnalyzer(name);
        return Objects.requireNonNull(analyzer, "no analyzer found for name : " + name);
    }

    private static class LazyObj<T> {

        private volatile T obj;
        private final Supplier<T> objSupplier;

        public LazyObj(Supplier<T> objSupplier) {
            this.objSupplier = objSupplier;
        }

        public T getOrCreate() {
            T obj = this.obj;
            if (obj == null) {
                synchronized (this) {
                    obj = this.obj;
                    if (obj == null) {
                        obj = this.obj = objSupplier.get();
                    }
                }
            }
            return obj;
        }

        public T getIfPresent() {
            return obj;
        }
    }
}