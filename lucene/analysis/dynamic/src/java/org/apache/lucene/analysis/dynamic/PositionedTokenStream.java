package org.apache.lucene.analysis.dynamic;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

public class PositionedTokenStream extends TokenFilter implements Comparable<PositionedTokenStream> {

    // Attributes to track
    private final OffsetAttribute offsetAttr;
    private final PositionIncrementAttribute posAttr;
    /**
     * Position tracker.
     */
    private int position;

    PositionedTokenStream(TokenStream input) {
        super(input);
        this.offsetAttr = input.addAttribute(OffsetAttribute.class);
        this.posAttr = input.addAttribute(PositionIncrementAttribute.class);
        this.position = 0;
    }

    /**
     * Returns the tracked current token position.
     *
     * @return The accumulated position increment attribute values.
     */
    public int getPosition() {
        return position;
    }

    /*
     * "TokenStream interface"
     */

    public final boolean incrementToken() throws IOException {
        boolean rtn = input.incrementToken();
        if (!rtn) {
            position = Integer.MAX_VALUE;
        }
        // Track accumulated position
        position += posAttr.getPositionIncrement();
        return rtn;
    }

    public void end() throws IOException {
        input.end();
        position = 0;
    }

    public void reset() throws IOException {
        input.reset();
        position = 0;
    }

    public void close() throws IOException {
        input.close();
        position = 0;
    }

    /**
     * Permit ordering by reading order: term offsets (start, then end) and term position
     */
    @Override
    public int compareTo(PositionedTokenStream that) {
        int a = this.offsetAttr.startOffset();
        int b = that.offsetAttr.startOffset();
        if (a == b) {
            a = this.offsetAttr.endOffset();
            b = that.offsetAttr.endOffset();
            if (a == b) {
                a = this.position;
                b = that.position;
            }
        }
        return a - b;
    }
}