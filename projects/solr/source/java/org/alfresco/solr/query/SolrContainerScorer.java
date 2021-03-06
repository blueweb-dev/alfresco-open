/*
 * #%L
 * Alfresco Solr
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.solr.query;

import java.io.IOException;

import org.alfresco.repo.search.impl.lucene.query.StructuredFieldPosition;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Weight;

/**
 * The scorer for structured field queries.
 * 
 * A document either matches or it does not, there for the frequency is reported
 * as 0.0f or 1.0.
 * 
 * 
 * 
 * @author andyh
 */
public class SolrContainerScorer extends Scorer
{
    // Unused
    Weight weight;

    // Positions of documents with multiple structure elements
    // e.g have mutiple paths, multiple categories or multiples entries in the
    // same category
    TermPositions root;

    // The Field positions that describe the structure we are trying to match
    StructuredFieldPosition[] positions;

    // Unused at the moment
    byte[] norms;

    // The minium document found so far
    int min = 0;

    // The max document found so far
    int max = 0;

    // The next root doc
    // -1 and it has gone off the end
    int rootDoc = 0;

    // Are there potentially more documents
    boolean more = true;

    // The frequency of the terms in the doc (0.0f or 1.0f)
    float freq = 0.0f;

    /**
     * The arguments here follow the same pattern as used by the PhraseQuery.
     * (It has the same unused arguments)
     * 
     * @param weight -
     *            curently unsued
     * @param root -
     *            the term positions for documents with multiple entries - this
     *            may be null, or contain no matches - it specifies those things
     *            that appear under multiple categories etc.
     * @param positions -
     *            the structured field positions - where terms should appear
     * @param similarity -
     *            used in the abstract scorer implementation
     * @param norms -
     *            unused
     */
    public SolrContainerScorer(Weight weight, TermPositions root, StructuredFieldPosition[] positions, Similarity similarity, byte[] norms)
    {
        super(similarity);
        this.weight = weight;
        this.positions = positions;
        this.norms = norms;
        this.root = root;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.lucene.search.Scorer#next()
     */
    public boolean next() throws IOException
    {
        if (!more)
        {
            // One of the search terms has no more docuements
            return false;
        }

        if (max == 0)
        {
            // We need to initialise
            // Just do a next on all terms and check if the first doc matches
            doNextOnAll();
            if (found())
            {
                return true;
            }
            // drop through to the normal find sequence
        }

        return findNext();
    }

    /**
     * @return boolean
     * @throws IOException
     */
    private boolean findNext() throws IOException
    {
        // Move to the next document

        while (more)
        {
            move(); // may set more to false
            if (found())
            {
                return true;
            }
        }

        // If we get here we must have no more documents
        return false;
    }

    /**
     * Check if we have found a match
     * 
     * @return boolean
     * @throws IOException
     */

    private boolean found() throws IOException
    {
        // No predicate test if there are no positions
        if (positions.length == 0)
        {
            return true;
        }

        // no more documents - no match
        if (!more)
        {
            return false;
        }

        // min and max must point to the same document
        if (min != max)
        {
            return false;
        }

        if (rootDoc != max)
        {
            return false;
        }

        // We have duplicate entries - suport should be improved but it is not used at the moment
        // This shuld work akin to the leaf scorer 
        // It would compact the index
        // The match must be in a known term range
        int count = root.freq();
        int start = 0;
        int end = -1;
        for (int i = 0; i < count; i++)
        {
            if (i == 0)
            {
                // First starts at zero
                start = 0;
                end = root.nextPosition() ;
            }
            else
            {
                start = end + 1;
                end = root.nextPosition() ;
            }

            if (check(start, end))
            {
                return true;
            }
        }

        // We had checks to do and they all failed.
        return false;
    }

    /*
     * We have all documents at the same state. Now we check the positions of
     * the terms.
     */

    private boolean check(int start, int end) throws IOException
    {
        int offset = checkTail(start, end, 0, 0, true);
        // Last match may fail
        if (offset == -1)
        {
            return false;
        }
        else
        {   
            return true;
        }
    }

    /**
     * For // type pattern matches we need to test patterns of variable greedyness.
     *
     * 
     * @param start - first term position
     * @param end - end term position marker
     * @param currentPosition - current path match index
     * @param currentOffset - current path position starting at zero (position being considered is start+offset)  
     * @return int
     * @throws IOException
     */
    private int checkTail(int start, int end, int currentPosition, int currentOffset, boolean checkLastMatch) throws IOException
    {
        // pre check last can match some combintaion or we are done
        if(checkLastMatch)
        {
            if(end-start > 1)
            {
                StructuredFieldPosition last = getLastPositionNotSelfCheck();
                if(last != null)
                {
                    if(last.matches(start, end, end-start-2) == -1)
                    {
                        return -1;
                    }
                }
            }
        }
        
        int offset = currentOffset;
        for (int i = currentPosition, l = positions.length; i < l; i++)
        {
            offset = positions[i].matches(start, end, offset);
            if (offset == -1)
            {
                return -1;
            }
            if (positions[i].isDescendant())
            {
                // go up in twos as there are two parts to a QName
                for (int j = offset; j < (end - start); j+=2)
                {
                    int newOffset = checkTail(start, end, i + 1, j, false);
                    if (newOffset != -1)
                    {
                        return newOffset;
                    }
                }
                return -1;
            }
        }
       
        if((start+offset+1) == end)
        {
            return offset;
        }
        else
        {
            return -1;
        }
    }

    private StructuredFieldPosition getLastPositionNotSelfCheck()
    {
        for(int i = positions.length-1; i >= 0; i--)
        {
            if(positions[i].linkSelf())
            {
                continue;
            }
            return positions[i];
        }
        return null;
    }
    
    /*
     * Move to the next position to consider for a match test
     */

    private void move() throws IOException
    {
        if (min == max)
        {
            // If we were at a match just do next on all terms
            // They all must move on
            doNextOnAll();
        }
        else
        {
            // We are in a range - try and skip to the max position on all terms
            // Only some need to move on - some may move past the current max and set a new target
            skipToMax();
        }
    }

    /*
     * Go through all the term positions and try and move to next document. Any
     * failure measn we have no more.
     * 
     * This can be used at initialisation and when moving away from an existing
     * match.
     * 
     * This will set min, max, more and rootDoc
     * 
     */
    private void doNextOnAll() throws IOException
    {
        // Do the terms
        int current;
        boolean first = true;
        for (int i = 0, l = positions.length; i < l; i++)
        {
            if (positions[i].getCachingTermPositions() != null)
            {
                if (positions[i].getCachingTermPositions().next())

                {
                    current = positions[i].getCachingTermPositions().doc();
                    adjustMinMax(current, first);
                    first = false;
                }
                else
                {
                    more = false;
                    return;
                }
            }
        }

        // Do the root term - it must always exists as the path could well have mutiple entries
        // If an entry in the index does not have a root terminal it is broken
        if (root.next())
        {
            rootDoc = root.doc();
            if(first)
            {
                adjustMinMax(rootDoc, first);
            }
        }
        else
        {
            more = false;
            return;
        }
        if (root.doc() < max)
        {
            if (root.skipTo(max))
            {
                rootDoc = root.doc();
            }
            else
            {
                more = false;
                return;
            }
        }
    }

    /*
     * Try and skip all those term positions at documents less than the current
     * max up to value. This is quite likely to fail and leave us with (min !=
     * max) but that is OK, we try again.
     * 
     * It is possible that max increases as we process terms, this is OK. We
     * just failed to skip to a given value of max and start doing the next.
     */
    private void skipToMax() throws IOException
    {
        // Do the terms
        int current;
        for (int i = 0, l = positions.length; i < l; i++)
        {
            if (i == 0)
            {
                min = max;
            }
            if (positions[i].getCachingTermPositions() != null)
            {
                if (positions[i].getCachingTermPositions().doc() < max)
                {
                    if (positions[i].getCachingTermPositions().skipTo(max))
                    {
                        current = positions[i].getCachingTermPositions().doc();
                        adjustMinMax(current, false);
                    }
                    else
                    {
                        more = false;
                        return;
                    }
                }
            }
        }

        // Do the root
        if (root.doc() < max)
        {
            if (root.skipTo(max))
            {
                rootDoc = root.doc();
            }
            else
            {
                more = false;
                return;
            }
        }
    }

    /*
     * Adjust the min and max values Convenience boolean to set or adjust the
     * minimum.
     */
    private void adjustMinMax(int doc, boolean setMin)
    {

        if (max < doc)
        {
            max = doc;
        }

        if (setMin)
        {
            min = doc;
        }
        else if (min > doc)
        {
            min = doc;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.lucene.search.Scorer#doc()
     */
    public int doc()
    {
        return max;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.lucene.search.Scorer#score()
     */
    public float score() throws IOException
    {
        return 1.0f;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.lucene.search.Scorer#skipTo(int)
     */
    public boolean skipTo(int target) throws IOException
    {
        max = target;
        return findNext();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.lucene.search.Scorer#explain(int)
     */
    public Explanation explain(int doc) throws IOException
    {
        // TODO: Work out what a proper explanation would be here? 
        Explanation tfExplanation = new Explanation();

        while (next() && doc() < doc)
        {
        }

        float phraseFreq = (doc() == doc) ? freq : 0.0f;
        tfExplanation.setValue(getSimilarity().tf(phraseFreq));
        tfExplanation.setDescription("tf(phraseFreq=" + phraseFreq + ")");

        return tfExplanation;
    }

}