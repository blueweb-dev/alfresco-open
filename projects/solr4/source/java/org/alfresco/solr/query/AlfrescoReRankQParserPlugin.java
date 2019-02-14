/*
 * #%L
 * Alfresco Solr 4
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

import com.carrotsearch.hppc.IntIntOpenHashMap;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.search.QueryRescorer;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TopFieldCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.search.*;
import org.apache.solr.handler.component.MergeStrategy;
import org.apache.solr.handler.component.QueryElevationComponent;
import org.apache.solr.request.SolrQueryRequest;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.ScoreDoc;
import com.carrotsearch.hppc.IntFloatOpenHashMap;

import org.apache.lucene.util.Bits;
import org.apache.solr.request.SolrRequestInfo;

import java.io.IOException;
import java.util.*;

/*
*
*  Syntax: q=*:*&rq={!rerank reRankQuery=$rqq reRankDocs=300 reRankWeight=3}
*
*/

public class AlfrescoReRankQParserPlugin extends QParserPlugin {

    public static final String NAME = "rerank";
    private static Query defaultQuery = new MatchAllDocsQuery();

    public void init(NamedList args) {
    }

    public QParser createParser(String query, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        return new ReRankQParser(query, localParams, params, req);
    }

    private class ReRankQParser extends QParser  {

        public ReRankQParser(String query, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
            super(query, localParams, params, req);
        }

        public Query parse() throws SyntaxError {
            String reRankQueryString = localParams.get("reRankQuery");
            boolean scale = localParams.getBool("scale", false);
            QParser reRankParser = QParser.getParser(reRankQueryString, null, req);
            Query reRankQuery = reRankParser.parse();

            int reRankDocs  = localParams.getInt("reRankDocs", 200);
            reRankDocs = Math.max(1, reRankDocs); //

            double reRankWeight = localParams.getDouble("reRankWeight",2.0d);

            int start = params.getInt(CommonParams.START,0);
            int rows = params.getInt(CommonParams.ROWS,10);
            int length = start+rows;
            return new ReRankQuery(reRankQuery, reRankDocs, reRankWeight, length, scale);
        }
    }

    private class ReRankQuery extends RankQuery {
        private Query mainQuery = defaultQuery;
        private Query reRankQuery;
        private int reRankDocs;
        private int length;
        private double reRankWeight;
        private boolean scale;
        private Map<BytesRef, Integer> boostedPriority;

        public int hashCode() {
            return mainQuery.hashCode()+reRankQuery.hashCode()+(int)reRankWeight+reRankDocs+(int)getBoost()+(scale ? 1 : 0);
        }

        public boolean equals(Object o) {
            if(o instanceof ReRankQuery) {
                ReRankQuery rrq = (ReRankQuery)o;
                return (mainQuery.equals(rrq.mainQuery) &&
                        reRankQuery.equals(rrq.reRankQuery) &&
                        reRankWeight == rrq.reRankWeight &&
                        reRankDocs == rrq.reRankDocs &&
                        getBoost() == rrq.getBoost() &&
                        scale == rrq.scale);
            }
            return false;
        }

        public ReRankQuery(Query reRankQuery, int reRankDocs, double reRankWeight, int length, boolean scale) {
            this.reRankQuery = reRankQuery;
            this.reRankDocs = reRankDocs;
            this.reRankWeight = reRankWeight;
            this.length = length;
            this.scale = scale;
        }

        public RankQuery wrap(Query _mainQuery) {
            if(_mainQuery != null){
                this.mainQuery = _mainQuery;
            }
            return  this;
        }

        public MergeStrategy getMergeStrategy() {
            return null;
        }

        public TopDocsCollector getTopDocsCollector(int len, SolrIndexSearcher.QueryCommand cmd, IndexSearcher searcher) throws IOException {

            if(this.boostedPriority == null) {
                SolrRequestInfo info = SolrRequestInfo.getRequestInfo();
                if(info != null) {
                    Map context = info.getReq().getContext();
                    this.boostedPriority = (Map<BytesRef, Integer>)context.get(QueryElevationComponent.BOOSTED_PRIORITY);
                }
            }

            return new ReRankCollector(reRankDocs, length, reRankQuery, reRankWeight, cmd, searcher, boostedPriority, scale);
        }

        public String toString(String s) {
            return "{!rerank mainQuery='"+mainQuery.toString()+
                    "' reRankQuery='"+reRankQuery.toString()+
                    "' reRankDocs="+reRankDocs+
                    " reRankWeigh="+reRankWeight+"}";
        }

        public String toString() {
            return toString(null);
        }

        public Query rewrite(IndexReader reader) throws IOException {
            Query q = mainQuery.rewrite(reader);
            if(q == mainQuery) {
                return this;
            } else {
                return clone().wrap(q);
            }
        }

        public ReRankQuery clone() {
            ReRankQuery clonedQuery =  new ReRankQuery(reRankQuery, reRankDocs, reRankWeight, length, scale);
            clonedQuery.setBoost(getBoost());
            return clonedQuery;
        }

        public void extractTerms(Set<Term> terms) {
            this.mainQuery.extractTerms(terms);

        }

        public Weight createWeight(IndexSearcher searcher) throws IOException{
            return new ReRankWeight(mainQuery, reRankQuery, reRankWeight, searcher);
        }
    }

    private class ReRankWeight extends Weight{
        private Query reRankQuery;
        private IndexSearcher searcher;
        private Weight mainWeight;
        private double reRankWeight;

        public ReRankWeight(Query mainQuery, Query reRankQuery, double reRankWeight, IndexSearcher searcher) throws IOException {
            this.reRankQuery = reRankQuery;
            this.searcher = searcher;
            this.reRankWeight = reRankWeight;
            this.mainWeight = mainQuery.createWeight(searcher);
        }

        public float getValueForNormalization() throws IOException {
            return mainWeight.getValueForNormalization();
        }

        public Scorer scorer(AtomicReaderContext context, Bits bits) throws IOException {
            return mainWeight.scorer(context, bits);
        }

        public Query getQuery() {
            return mainWeight.getQuery();
        }

        public void normalize(float norm, float topLevelBoost) {
            mainWeight.normalize(norm, topLevelBoost);
        }

        public Explanation explain(AtomicReaderContext context, int doc) throws IOException {
            Explanation mainExplain = mainWeight.explain(context, doc);
            return new QueryRescorer(reRankQuery) {
                @Override
                protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
                    float score = firstPassScore;
                    if (secondPassMatches) {
                        score += reRankWeight * secondPassScore;
                    }
                    return score;
                }
            }.explain(searcher, mainExplain, context.docBase+doc);
        }
    }

    private class ReRankCollector extends TopDocsCollector {

        private Query reRankQuery;
        private TopDocsCollector  mainCollector;
        private IndexSearcher searcher;
        private int reRankDocs;
        private int length;
        private double reRankWeight;
        private Map<BytesRef, Integer> boostedPriority;
        private float minScore = Float.MAX_VALUE;
        private float maxScore = -Float.MAX_VALUE;
        private Scorer localScorer;
        private boolean scale;

        public ReRankCollector(int reRankDocs,
                               int length,
                               Query reRankQuery,
                               double reRankWeight,
                               SolrIndexSearcher.QueryCommand cmd,
                               IndexSearcher searcher,
                               Map<BytesRef, Integer> boostedPriority,
                               boolean scale) throws IOException {
            super(null);
            this.reRankQuery = reRankQuery;
            this.reRankDocs = reRankDocs;
            this.length = length;
            this.boostedPriority = boostedPriority;
            this.scale = scale;
            Sort sort = cmd.getSort();
            if(sort == null) {
                this.mainCollector = TopScoreDocCollector.create(Math.max(this.reRankDocs, length),true);
            } else {
                sort = sort.rewrite(searcher);
                this.mainCollector = TopFieldCollector.create(sort, Math.max(this.reRankDocs, length), false, true, true, true);
            }
            this.searcher = searcher;
            this.reRankWeight = reRankWeight;
        }

        public boolean acceptsDocsOutOfOrder() {
            return false;
        }

        public void collect(int doc) throws IOException {
            mainCollector.collect(doc);
        }

        public void setScorer(Scorer scorer) throws IOException {
            mainCollector.setScorer(scorer);
        }

        public void setNextReader(AtomicReaderContext context) throws IOException{
            mainCollector.setNextReader(context);
        }

        public int getTotalHits() {
            return mainCollector.getTotalHits();
        }

        public TopDocs topDocs(int start, int howMany) {

            try {

                TopDocs mainDocs = mainCollector.topDocs(0,  Math.max(reRankDocs, length));

                if(mainDocs.totalHits == 0 || mainDocs.scoreDocs.length == 0) {
                    return mainDocs;
                }

                if(reRankDocs == 0) {
                    scaleScores(mainDocs, new HashMap());
                    return mainDocs;
                }

                if(boostedPriority != null) {
                    SolrRequestInfo info = SolrRequestInfo.getRequestInfo();
                    Map requestContext = null;
                    if(info != null) {
                        requestContext = info.getReq().getContext();
                    }

                    IntIntOpenHashMap boostedDocs = QueryElevationComponent.getBoostDocs((SolrIndexSearcher)searcher, boostedPriority, requestContext);

                    ScoreDoc[] mainScoreDocs = mainDocs.scoreDocs;
                    ScoreDoc[] reRankScoreDocs = new ScoreDoc[Math.min(mainScoreDocs.length, reRankDocs)];
                    System.arraycopy(mainScoreDocs,0,reRankScoreDocs,0,reRankScoreDocs.length);

                    mainDocs.scoreDocs = reRankScoreDocs;

                    Map<Integer, Float> scoreMap = getScoreMap(mainDocs.scoreDocs, mainDocs.scoreDocs.length);

                    TopDocs rescoredDocs = new QueryRescorer(reRankQuery) {
                        @Override
                        protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
                            float score = firstPassScore;
                            if (secondPassMatches) {
                                score += reRankWeight * secondPassScore;
                            }
                            return score;
                        }
                    }.rescore(searcher, mainDocs, mainDocs.scoreDocs.length);

                    Arrays.sort(rescoredDocs.scoreDocs, new BoostedComp(boostedDocs, mainDocs.scoreDocs, rescoredDocs.getMaxScore()));

                    //Lower howMany if we've collected fewer documents.
                    howMany = Math.min(howMany, mainScoreDocs.length);

                    if(howMany == rescoredDocs.scoreDocs.length) {
                        if(scale) {
                            scaleScores(rescoredDocs, scoreMap);
                        }
                        return rescoredDocs; // Just return the rescoredDocs
                    } else if(howMany > rescoredDocs.scoreDocs.length) {
                        //We need to return more then we've reRanked, so create the combined page.
                        ScoreDoc[] scoreDocs = new ScoreDoc[howMany];
                        System.arraycopy(mainScoreDocs, 0, scoreDocs, 0, scoreDocs.length); //lay down the initial docs
                        System.arraycopy(rescoredDocs.scoreDocs, 0, scoreDocs, 0, rescoredDocs.scoreDocs.length);//overlay the re-ranked docs.
                        rescoredDocs.scoreDocs = scoreDocs;
                        if(scale) {
                            scaleScores(rescoredDocs, scoreMap);
                        }
                        return rescoredDocs;
                    } else {
                        //We've rescored more then we need to return.
                        ScoreDoc[] scoreDocs = new ScoreDoc[howMany];
                        System.arraycopy(rescoredDocs.scoreDocs, 0, scoreDocs, 0, howMany);
                        rescoredDocs.scoreDocs = scoreDocs;
                        if(scale) {
                            scaleScores(rescoredDocs, scoreMap);
                        }
                        return rescoredDocs;
                    }

                } else {

                    ScoreDoc[] mainScoreDocs   = mainDocs.scoreDocs;

                      /*
                      *  Create the array for the reRankScoreDocs.
                      */
                    ScoreDoc[] reRankScoreDocs = new ScoreDoc[Math.min(mainScoreDocs.length, reRankDocs)];

                      /*
                      *  Copy the initial results into the reRankScoreDocs array.
                      */
                    System.arraycopy(mainScoreDocs, 0, reRankScoreDocs, 0, reRankScoreDocs.length);

                    mainDocs.scoreDocs = reRankScoreDocs;

                    Map<Integer, Float> scoreMap = getScoreMap(mainDocs.scoreDocs, mainDocs.scoreDocs.length);

                    TopDocs rescoredDocs = new QueryRescorer(reRankQuery) {
                        @Override
                        protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
                            float score = firstPassScore;
                            if (secondPassMatches) {
                                score += reRankWeight * secondPassScore;
                            }
                            return score;
                        }
                    }.rescore(searcher, mainDocs, mainDocs.scoreDocs.length);

                    //Lower howMany to return if we've collected fewer documents.
                    howMany = Math.min(howMany, mainScoreDocs.length);

                    if(howMany == rescoredDocs.scoreDocs.length) {
                        if(scale) {
                            scaleScores(rescoredDocs, scoreMap);
                        }
                        return rescoredDocs; // Just return the rescoredDocs
                    } else if(howMany > rescoredDocs.scoreDocs.length) {

                        //We need to return more then we've reRanked, so create the combined page.
                        ScoreDoc[] scoreDocs = new ScoreDoc[howMany];
                        //lay down the initial docs
                        System.arraycopy(mainScoreDocs, 0, scoreDocs, 0, scoreDocs.length);
                        //overlay the rescoreds docs
                        System.arraycopy(rescoredDocs.scoreDocs, 0, scoreDocs, 0, rescoredDocs.scoreDocs.length);
                        rescoredDocs.scoreDocs = scoreDocs;
                        if(scale) {
                            assert(scoreMap != null);
                            scaleScores(rescoredDocs, scoreMap);
                        }
                        return rescoredDocs;
                    } else {
                        //We've rescored more then we need to return.
                        ScoreDoc[] scoreDocs = new ScoreDoc[howMany];
                        System.arraycopy(rescoredDocs.scoreDocs, 0, scoreDocs, 0, howMany);
                        rescoredDocs.scoreDocs = scoreDocs;
                        if(scale) {
                            scaleScores(rescoredDocs, scoreMap);
                        }
                        return rescoredDocs;
                    }
                }
            } catch (Exception e) {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
            }
        }
    }

    private void scaleScores(TopDocs topDocs, Map<Integer, Float> scoreMap) {

        float maxScore = topDocs.getMaxScore();
        float newMax = -Float.MAX_VALUE;

        for(ScoreDoc scoreDoc : topDocs.scoreDocs) {
            float score = scoreDoc.score;

            Float oldScore = scoreMap.get(scoreDoc.doc);
            if(oldScore == null || score == oldScore.floatValue()) {
                scoreDoc.score = score / maxScore;
            } else {
                scoreDoc.score = (score / maxScore)+1;
            }

            if(scoreDoc.score > newMax) {
                newMax = scoreDoc.score;
            }
        }

        assert(newMax <= 2);
        topDocs.setMaxScore(newMax);
    }

    private Map<Integer, Float> getScoreMap(ScoreDoc[] scoreDocs, int num) {
        Map<Integer, Float> scoreMap = new HashMap();
        for(int i=0; i<num; i++) {
            ScoreDoc doc = scoreDocs[i];
            scoreMap.put(doc.doc, doc.score);
        }
        return scoreMap;
    }

    public class BoostedComp implements Comparator {
        IntFloatOpenHashMap boostedMap;

        public BoostedComp(IntIntOpenHashMap boostedDocs, ScoreDoc[] scoreDocs, float maxScore) {
            this.boostedMap = new IntFloatOpenHashMap(boostedDocs.size()*2);

            for(int i=0; i<scoreDocs.length; i++) {
                if(boostedDocs.containsKey(scoreDocs[i].doc)) {
                    boostedMap.put(scoreDocs[i].doc, maxScore+boostedDocs.lget());
                } else {
                    break;
                }
            }
        }

        public int compare(Object o1, Object o2) {
            ScoreDoc doc1 = (ScoreDoc) o1;
            ScoreDoc doc2 = (ScoreDoc) o2;
            float score1 = doc1.score;
            float score2 = doc2.score;
            if(boostedMap.containsKey(doc1.doc)) {
                score1 = boostedMap.lget();
            }

            if(boostedMap.containsKey(doc2.doc)) {
                score2 = boostedMap.lget();
            }

            if(score1 > score2) {
                return -1;
            } else if(score1 < score2) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}