package org.apache.lucene.analysis.dynamic;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.dynamic.util.ReaderCloneFactory;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class PrefixBasedDynamicAnalyzer extends Analyzer {

    private final String prefix;
    private final int gap;
    private final List<Analyzer> defaultAnalyzers;
    private final Map<String, List<Analyzer>> prefixAnalyzersMap;

    public PrefixBasedDynamicAnalyzer(String prefix, int gap, List<Analyzer> defaultAnalyzers, Map<String, List<Analyzer>> prefixAnalyzersMap) {
        this.prefix = Objects.requireNonNull(prefix);
        this.gap = Math.max(0, gap);
        this.prefixAnalyzersMap = prefixAnalyzersMap;
        this.defaultAnalyzers = defaultAnalyzers;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        PositionedTokenStreams tokenStream = new PositionedTokenStreams(gap);
        return new TokenStreamComponents(new Consumer<Reader>() {
            @Override
            public void accept(Reader reader) {
                ReaderCloneFactory.ReaderCloner cloner = ReaderCloneFactory.getCloner(reader, prefix);
                List<Analyzer> analyzers = Optional.ofNullable(cloner.getValueAfterPrefix())
                        .map(prefix -> prefixAnalyzersMap.getOrDefault(prefix, defaultAnalyzers))
                        .orElse(defaultAnalyzers);
                TokenStream[] streams = new TokenStream[analyzers.size()];
                int i = 0;

                for (Analyzer analyzer : analyzers) {
                    streams[i++] = createTokenStream(analyzer, fieldName, cloner.giveAClone());
                }
                tokenStream.setTokenStreams(streams);
            }
        }, tokenStream);
    }

    private TokenStream createTokenStream(Analyzer analyzer, String field, Reader reader) {
        return analyzer.tokenStream(field, reader);
    }
}