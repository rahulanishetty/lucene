package org.apache.lucene.analysis.dynamic;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.LuceneTestCase;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TestPrefixBasedDynamicAnalyzer extends LuceneTestCase {

    public void testSingleAnalyzer() throws IOException {
        EnglishAnalyzer en = new EnglishAnalyzer();
        StandardAnalyzer std = new StandardAnalyzer();
        Map<String, List<Analyzer>> analyzerMap = Map.of("en", List.of(en), "std", List.of(std));
        PrefixBasedDynamicAnalyzer a = new PrefixBasedDynamicAnalyzer("[", 1000, List.of(en), analyzerMap);
        assertAnalyzesTo(a, "foo bar FOO BAR", new Object[][]{
                new Object[]{"foo", 1, 0, 3},
                new Object[]{"bar", 1, 4, 7},
                new Object[]{"foo", 1, 8, 11},
                new Object[]{"bar", 1, 12, 15}
        });
        assertAnalyzesTo(a, "[en]foo bar FOO BAR", new Object[][]{
                new Object[]{"foo", 1, 0, 3},
                new Object[]{"bar", 1, 4, 7},
                new Object[]{"foo", 1, 8, 11},
                new Object[]{"bar", 1, 12, 15}
        });
        assertAnalyzesTo(a, "[std]foo bar FOO BAR", new Object[][]{
                new Object[]{"foo", 1, 0, 3},
                new Object[]{"bar", 1, 4, 7},
                new Object[]{"foo", 1, 8, 11},
                new Object[]{"bar", 1, 12, 15}
        });
    }

    public void testMultipleAnalyzers() throws IOException {
        EnglishAnalyzer en = new EnglishAnalyzer();
        StandardAnalyzer std = new StandardAnalyzer();
        WhitespaceAnalyzer white = new WhitespaceAnalyzer();
        Map<String, List<Analyzer>> analyzerMap = Map.of("en", List.of(en), "std", List.of(std, white));
        PrefixBasedDynamicAnalyzer a = new PrefixBasedDynamicAnalyzer("[", 1000, List.of(en, std, white), analyzerMap);
        assertAnalyzesTo(a, "foo  !    bar_123 .  FOO <> BAR !", new Object[][]{
                new Object[]{"foo", 1, 0, 3},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{"foo", 1, 21, 24},
                new Object[]{"bar", 1, 28, 31},

                new Object[]{"foo", 1001, 0, 3},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{"foo", 1, 21, 24},
                new Object[]{"bar", 1, 28, 31},

                new Object[]{"foo", 1001, 0, 3},
                new Object[]{"!", 1, 5, 6},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{".", 1, 18, 19},
                new Object[]{"FOO", 1, 21, 24},
                new Object[]{"<>", 1, 25, 27},
                new Object[]{"BAR", 1, 28, 31},
                new Object[]{"!", 1, 32, 33},
        });

        assertAnalyzesTo(a, "[unknown]foo  !    bar_123 .  FOO <> BAR !", new Object[][]{
                new Object[]{"foo", 1, 0, 3},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{"foo", 1, 21, 24},
                new Object[]{"bar", 1, 28, 31},

                new Object[]{"foo", 1001, 0, 3},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{"foo", 1, 21, 24},
                new Object[]{"bar", 1, 28, 31},

                new Object[]{"foo", 1001, 0, 3},
                new Object[]{"!", 1, 5, 6},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{".", 1, 18, 19},
                new Object[]{"FOO", 1, 21, 24},
                new Object[]{"<>", 1, 25, 27},
                new Object[]{"BAR", 1, 28, 31},
                new Object[]{"!", 1, 32, 33},
        });

        assertAnalyzesTo(a, "[std]foo  !    bar_123 .  FOO <> BAR !", new Object[][]{
                new Object[]{"foo", 1, 0, 3},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{"foo", 1, 21, 24},
                new Object[]{"bar", 1, 28, 31},

                new Object[]{"foo", 1001, 0, 3},
                new Object[]{"!", 1, 5, 6},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{".", 1, 18, 19},
                new Object[]{"FOO", 1, 21, 24},
                new Object[]{"<>", 1, 25, 27},
                new Object[]{"BAR", 1, 28, 31},
                new Object[]{"!", 1, 32, 33},
        });

        assertAnalyzesTo(a, "[en]foo  !    bar_123 .  FOO <> BAR !", new Object[][]{
                new Object[]{"foo", 1, 0, 3},
                new Object[]{"bar_123", 1, 10, 17},
                new Object[]{"foo", 1, 21, 24},
                new Object[]{"bar", 1, 28, 31},
        });
    }

    public void testTryIndexing() throws IOException {
        EnglishAnalyzer en = new EnglishAnalyzer();
        StandardAnalyzer std = new StandardAnalyzer();
        WhitespaceAnalyzer white = new WhitespaceAnalyzer();
        Map<String, List<Analyzer>> analyzerMap = Map.of("en", List.of(en, white), "std", List.of(std, white));
        PrefixBasedDynamicAnalyzer a = new PrefixBasedDynamicAnalyzer("[", 1000, List.of(en, std, white), analyzerMap);
        ByteBuffersDirectory byteBuffersDirectory = new ByteBuffersDirectory();
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(a);
        IndexWriter indexWriter = new IndexWriter(byteBuffersDirectory, indexWriterConfig);
        try {
            TextField field = new TextField("dummy", "[std]foo  !    bar_123 .  FOO <> BAR !", Field.Store.NO);
            indexWriter.addDocument(List.of(field));
            indexWriter.flush();
            indexWriter.commit();
        } finally {
            indexWriter.close();
            byteBuffersDirectory.close();
        }
    }

    private void assertAnalyzesTo(Analyzer a, String input, Object[][] result) throws IOException {
        TokenStream tokenStream = a.tokenStream("dummy", input);
        CharTermAttribute termAtt = tokenStream.getAttribute(CharTermAttribute.class);
        PositionIncrementAttribute positionIncrementAttribute = tokenStream.getAttribute(PositionIncrementAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
        tokenStream.reset();
        tokenStream.clearAttributes();
        for (Object[] objects : result) {
            assertTrue(tokenStream.incrementToken());
            assertEquals("expected term:" + objects[0] + ", found : " + termAtt.toString(), objects[0], termAtt.toString());
            assertEquals("expected position:" + objects[1] + ", found : " + positionIncrementAttribute.getPositionIncrement(),
                    objects[1], positionIncrementAttribute.getPositionIncrement());
            assertEquals("expected startOffset:" + objects[2] + ", found : " + offsetAttribute.startOffset(),
                    objects[2], offsetAttribute.startOffset());
            assertEquals("expected startOffset:" + objects[3] + ", found : " + offsetAttribute.endOffset(),
                    objects[3], offsetAttribute.endOffset());
            tokenStream.clearAttributes();
        }
        tokenStream.end();
        tokenStream.close();
    }
}
