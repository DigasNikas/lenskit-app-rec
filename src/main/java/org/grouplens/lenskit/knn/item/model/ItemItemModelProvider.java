/*
 * LensKit, an open source recommender systems toolkit.
 * Copyright 2010-2014 LensKit Contributors.  See CONTRIBUTORS.md.
 * Work on LensKit has been funded by the National Science Foundation under
 * grants IIS 05-34939, 08-08692, 08-12148, and 10-17697.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.lenskit.knn.item.model;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.longs.*;
import org.grouplens.lenskit.transform.threshold.Threshold;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;
import org.lenskit.util.UnlimitedScoredIdAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;
import org.lenskit.inject.Transient;
import org.lenskit.knn.item.ItemSimilarity;
import org.lenskit.knn.item.ItemSimilarityThreshold;
import org.lenskit.knn.item.MinCommonUsers;
import org.lenskit.knn.item.ModelSize;
import org.lenskit.util.ProgressLogger;
import org.lenskit.util.collections.LongUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;
import java.io.*;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * Build an item-item CF model from rating data.
 * This builder takes a very simple approach. It does not allow for vector
 * normalization and truncates on the fly.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
@NotThreadSafe
public class ItemItemModelProvider implements Provider<ItemItemModel> {
    private static final Logger logger = LoggerFactory.getLogger(ItemItemModelProvider.class);

    private final ItemSimilarity itemSimilarity;
    private static ItemItemBuildContext buildContext;
    private final Threshold threshold;
    private final NeighborIterationStrategy neighborStrategy;
    private final int minCommonUsers;
    private final int modelSize;
    private PrintWriter pw;
    private ProgressLogger progress;

    @Inject
    public ItemItemModelProvider(@Transient ItemSimilarity similarity,
                                 @Transient ItemItemBuildContext context,
                                 @Transient @ItemSimilarityThreshold Threshold thresh,
                                 @Transient NeighborIterationStrategy nbrStrat,
                                 @MinCommonUsers int minCU,
                                 @ModelSize int size) {
        itemSimilarity = similarity;
        buildContext = getContext();
        threshold = thresh;
        neighborStrategy = nbrStrat;
        minCommonUsers = minCU;
        modelSize = size;
    }

    @Override
    public SimilarityMatrixModel get() {
        LongSortedSet allItems = buildContext.getItems();
        final int nitems = allItems.size();

        logger.info("building item-item model for {} items", nitems);
        logger.debug("using similarity function {}", itemSimilarity);
        logger.debug("similarity function is {}",
                     itemSimilarity.isSparse() ? "sparse" : "non-sparse");
        logger.debug("similarity function is {}",
                     itemSimilarity.isSymmetric() ? "symmetric" : "non-symmetric");

        pw = null;
        try {
            File fileTwo = new File("similarities.tmp");
            FileOutputStream fos = new FileOutputStream(fileTwo);
            pw = new PrintWriter(fos);
        }catch(Exception e){}

        progress = ProgressLogger.create(logger)
                .setCount(nitems)
                .setLabel("item-item model build")
                .setWindow(50)
                .start();

        int n_threads = Integer.parseInt("16");
        Thread Pool[] = new Thread[n_threads];
        int items_by_thread = nitems/n_threads;

        logger.info("Building {} Threads", n_threads);
        for(int i = 0; i < n_threads; i++ ){

            int items = i*items_by_thread;
            if (i < n_threads -1) {
                LongIterator outer = allItems.subSet(items, items + items_by_thread).iterator();
                Pool[i] = new Thread(new MyThread(outer, itemSimilarity, pw, buildContext,
                        threshold, neighborStrategy, minCommonUsers, progress));
            }
            else {
                int k = (nitems - ((n_threads - 1) * items_by_thread));
                LongIterator outer = allItems.subSet(items, items + k).iterator();
                Pool[i] = new Thread(new MyThread(outer, itemSimilarity, pw, buildContext,
                        threshold,neighborStrategy,minCommonUsers, progress));
            }
            Pool[i].start();

        }
        logger.info("Threads Running");
        Stopwatch timer;
        timer = Stopwatch.createStarted();
        try {
            for (int j = 0; j < n_threads; j++) {
                Pool[j].join();
            }
            for (int j = 0; j < n_threads; j++) {
                Pool[j]=null;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        timer.stop();
        logger.info("Thread computation done in {}", timer);

        pw.close();
        progress.finish();

        buildContext = null;
        pw = null;
        progress = null;

        logger.info("Building Object from similarities.csv");
        Stopwatch timerX;
        timerX = Stopwatch.createStarted();
        Long2ObjectMap<ScoredIdAccumulator> rows = buildRows(allItems);
        timerX.stop();

        logger.info("built object in {}",timerX);
        return new SimilarityMatrixModel(finishRows(rows));
    }

    private Long2ObjectMap<ScoredIdAccumulator> makeAccumulators(LongSet items) {
        Long2ObjectMap<ScoredIdAccumulator> rows = new Long2ObjectOpenHashMap<>(items.size());
        LongIterator iter = items.iterator();
        while (iter.hasNext()) {
            long item = iter.nextLong();
            ScoredIdAccumulator accum;
            if (modelSize == 0) {
                accum = new UnlimitedScoredIdAccumulator();
            } else {
                accum = new TopNScoredIdAccumulator(modelSize);
            }
            rows.put(item, accum);
        }
        return rows;
    }

    private Long2ObjectMap<Long2DoubleMap> finishRows(Long2ObjectMap<ScoredIdAccumulator> rows) {
        Long2ObjectMap<Long2DoubleMap> results = new Long2ObjectOpenHashMap<>(rows.size());
        for (Long2ObjectMap.Entry<ScoredIdAccumulator> e: rows.long2ObjectEntrySet()) {
            results.put(e.getLongKey(), e.getValue().finishMap());
        }
        return results;
    }

    private ItemItemBuildContext getContext(){
        ItemItemBuildContext buildContext = null;
        try {
            InputStream fis = new FileInputStream("initial_model.data");
            InputStream buffer = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream (buffer);
            buildContext = (ItemItemBuildContext) ois.readObject();
            ois.close();
        }
        catch(Exception e) {
            System.err.println(e.toString());
            e.printStackTrace(System.err);
            System.exit(1);
        }
        return buildContext;
    }

    private Long2ObjectMap<ScoredIdAccumulator> buildRows(LongSortedSet allItems){
        Long2ObjectMap<ScoredIdAccumulator> rows = makeAccumulators(allItems);
        try {
            File toRead = new File("similarities.tmp");
            FileInputStream fis = new FileInputStream(toRead);

            Scanner sc = new Scanner(fis);

            String currentLine;
            while(sc.hasNextLine()){
                currentLine=sc.nextLine();
                StringTokenizer st=new StringTokenizer(currentLine,",",false);
                rows.get(Long.valueOf(st.nextToken())).put(Long.valueOf(st.nextToken()), Double.valueOf(st.nextToken()));
            }
            fis.close();
        }
        catch(Exception e){}
        return rows;
    }
}

class MyThread extends Thread {
    private static volatile ItemSimilarity itemSimilarity;
    private final LongIterator outer;
    private PrintWriter pw;
    private static volatile ItemItemBuildContext buildContext;
    private static volatile Threshold threshold;
    private static volatile NeighborIterationStrategy neighborStrategy;
    private static volatile int minCommonUsers;
    private static volatile ProgressLogger progress;

    public MyThread(LongIterator Outer, ItemSimilarity Similarity, PrintWriter Pw,
                    ItemItemBuildContext BuildContext, Threshold Threshold, NeighborIterationStrategy NeighborStrategy,
                    int MinCommonUsers, ProgressLogger Progress){
        outer = Outer;
        itemSimilarity = Similarity;
        pw = Pw;
        buildContext = BuildContext;
        threshold = Threshold;
        neighborStrategy = NeighborStrategy;
        minCommonUsers = MinCommonUsers;
        progress = Progress;
    }

    public void run() {
        OUTER:
        while (outer.hasNext()) {
            final long itemId1 = outer.nextLong();
            //System.out.println(itemId1);
            SparseVector vec1 = buildContext.itemVector(itemId1);
            if (vec1.size() < minCommonUsers) {
                // if it doesn't have enough users, it can't have enough common users
                progress.advance();
                continue OUTER;
            }

            LongIterator itemIter = neighborStrategy.neighborIterator(buildContext, itemId1,
                    itemSimilarity.isSymmetric());

            INNER:
            while (itemIter.hasNext()) {
                long itemId2 = itemIter.nextLong();
                if (itemId1 != itemId2) {
                    SparseVector vec2 = buildContext.itemVector(itemId2);
                    if (!LongUtils.hasNCommonItems(vec1.keySet(), vec2.keySet(), minCommonUsers)) {
                        // items have insufficient users in common, skip them
                        continue INNER;
                    }

                    double sim = itemSimilarity.similarity(itemId1, vec1, itemId2, vec2);
                    if (threshold.retain(sim)) {
                        if (itemSimilarity.isSymmetric()) {
                            try {
                                pw.println(itemId2 + "," + itemId1 + "," + sim);
                                pw.flush();
                            } catch (Exception e) {}
                        }
                    }
                }
            }
            progress.advance();
        }
    }
}
