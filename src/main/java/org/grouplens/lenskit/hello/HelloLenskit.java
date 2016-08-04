/*
 * Copyright 2011 University of Minnesota
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.grouplens.lenskit.hello;

import com.google.common.base.Throwables;
import org.grouplens.lenskit.util.io.CompressionMode;
import org.lenskit.LenskitConfiguration;
import org.lenskit.LenskitRecommender;
import org.lenskit.LenskitRecommenderEngine;
import org.lenskit.api.ItemBasedItemRecommender;
import org.lenskit.api.Result;
import org.lenskit.api.ResultList;
import org.lenskit.config.ConfigHelpers;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.dao.file.StaticDataSource;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.entities.CommonTypes;
import org.lenskit.data.entities.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.LenskitRecommenderEngineLoader;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.common.base.Stopwatch;

/**
 * Demonstration app for LensKit. This application builds an item-item CF model
 * from a CSV file, then generates recommendations for a user.
 *
 * Usage: java org.grouplens.lenskit.hello.HelloLenskit ratings.csv user
 */
public class HelloLenskit implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(HelloLenskit.class);

    public static void main(String[] args) {
        if (args.length == 0)
            System.err.println("Proper Usage is: lenskit-hello [Train or Test] [Aptoide Config File]");
        if(args[0].equals("Train") || args[0].equals("Test")) {
            HelloLenskit hello = new HelloLenskit(args[0], args[1]);
            try {
                hello.run();
            } catch (RuntimeException e) {
                System.err.println(e.toString());
                e.printStackTrace(System.err);
                System.exit(1);
            }
        }
        else System.err.println("Please select either Train or Test");
    }
    private String mode;
    private Path dataFile;
    private static FileReader aptoide_config;
    private List<Long> items;
    private List<List<Long>> total_items = new ArrayList<List<Long>>();
    private List<String> lines = new ArrayList<>();
    private static List<String> config_file = new ArrayList<>();
    private static final String cvsSplitBy = ",";
    private static final String newLine = "\n";

    public HelloLenskit(String ModeInput, String ConfigInput) {
        mode = ModeInput;
        try {
            aptoide_config = new FileReader(ConfigInput);
        } catch (IOException e) {
            System.err.println("Please insert a valid Aptoide Config File");
            e.printStackTrace();
            System.exit(1);
        }
        logger.info("reading aptoide config file");
        readAptoideConfigFile();
        dataFile = Paths.get(getDataFile());
        /*String csvFile = getTestInputFile();
        String line = "";
        String[] str;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        for (String arg: lines) {
            items = new ArrayList<>(arg.length());
            str = arg.split(cvsSplitBy);
            for (String st: str) {
                items.add(Long.parseLong(st));
            }
            total_items.add(items);
        }*/
    }

    // Method to read aptoide config file
    // This file must have:
    //  1 - Trained Model file (either for input and output);
    //  2 - Configuration file for training;
    //  3 - Data file (.yml required);
    //  4 - Test input file;
    //  5 - Test output file;
    //  6 - Number of recommendations needed per item;
    //  7 - Number of Threads for testing;

    public static void readAptoideConfigFile(){
        String line = "";
        try (BufferedReader br = new BufferedReader(aptoide_config)) {
            while ((line = br.readLine()) != null) {
                config_file.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public String getModelFile(){
        return config_file.get(0);
    }

    public String getConfigFile(){
        return config_file.get(1);
    }

    public String getDataFile(){
        return config_file.get(2);
    }

    public String getTestInputFile(){
        return config_file.get(3);
    }

    public String getTestOutPutFile(){
        return config_file.get(4);
    }

    public String getAmountRecs(){
        return config_file.get(5);
    }

    public String getNumberThreads(){
        return config_file.get(6);
    }

    public void run() {
        // We first need to configure the data access.
        // We will load data from a static data source; you could implement your own DAO
        // on top of a database of some kind
        DataAccessObject dao;
        try {
            StaticDataSource data = StaticDataSource.load(dataFile);
            // get the data from the DAO
            dao = data.get();
        } catch (IOException e) {
            logger.error("cannot load data", e);
            throw Throwables.propagate(e);
        }

        // If we select Train we load the configuration file
        // and train the model, followed by writing this one into disk.
        // If we select Test we load the previously trained model together
        // with a test input file, this file has items for querys and it's
        // results will be written in the test ouput file.

        if (mode.equals("Train")) {
            // Next: load the LensKit algorithm configuration
            LenskitConfiguration config;
            try {
                config = ConfigHelpers.load(new File(getConfigFile()));
            } catch (IOException e) {
                throw new RuntimeException("could not load configuration", e);
            }

            // There are more parameters, roles, and components that can be set. See the
            // JavaDoc for each recommender algorithm for more information.

            // Now that we have a configuration, build a recommender engine from the configuration
            // and data source. This will compute the similarity matrix and return a recommender
            // engine that uses it.

            Stopwatch timer1 = Stopwatch.createStarted();
            LenskitRecommenderEngine engine = LenskitRecommenderEngine.build(config, dao);
            timer1.stop();
            logger.info("built recommender engine in {}", timer1);
            File output = new File(getModelFile());
            CompressionMode comp = CompressionMode.autodetect(output);
            logger.info("writing model to {}", output);
            try (OutputStream raw = new FileOutputStream(output); OutputStream stream = comp.wrapOutput(raw)) {
                Stopwatch timer_writer = Stopwatch.createStarted();
                engine.write(stream);
                timer_writer.stop();
                logger.info("wrote model in {}", timer_writer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if(mode.equals("Test")){
            converter();
            FileWriter fw = null;
            File modelFile = new File(getModelFile());
            Stopwatch timerX;
            LenskitRecommenderEngineLoader loader;
            Object input;
            LenskitRecommenderEngine engine;
            try {
                timerX = Stopwatch.createStarted();
                loader = LenskitRecommenderEngine.newLoader();
                input = new FileInputStream(modelFile);
                engine = loader.load((InputStream) input);
                logger.info("loading recommender from {}", modelFile);
                timerX.stop();
                logger.info("loaded recommender engine in {}", timerX);
                fw = new FileWriter(getTestOutPutFile());
                // Finally, get the recommender and use it.
                try (LenskitRecommender rec = engine.createRecommender(dao)) {
                    logger.info("obtained recommender from engine");
                    // we want to recommend items
                    ItemBasedItemRecommender irec = rec.getItemBasedItemRecommender();
                    assert irec != null; // not null because we configured one
                    if (irec == null) {
                        logger.error("recommender has no global recommender");
                        throw new UnsupportedOperationException("no global recommender");
                    }

                    Stopwatch thread_timer = Stopwatch.createStarted();

                    int n_threads = Integer.parseInt(getNumberThreads());
                    Thread Pool[] = new Thread[n_threads];
                    int items_by_thread = total_items.size()/n_threads;

                    logger.info("Building {} Threads", n_threads);
                    for(int i = 0; i < n_threads; i++ ){
                        if (i < n_threads -1)
                            Pool[i] = new Thread(new MyThread(total_items, items_by_thread * i, items_by_thread, getAmountRecs(), irec, dao, fw));
                        else
                            Pool[i] = new Thread(new MyThread(total_items, items_by_thread * i, (total_items.size()-((n_threads-1)*items_by_thread)), getAmountRecs(), irec, dao, fw));
                        Pool[i].start();
                    }
                    logger.info("Threads Running");
                    for (int j = 0; j < n_threads; j++) {
                        Pool[j].join();
                    }
                    thread_timer.stop();
                    logger.info("recommended in {}", thread_timer);
                    /*Stopwatch thread_timer = Stopwatch.createStarted();
                    for (List<Long> used_items : total_items) {
                        logger.info("using {} reference items: {}", used_items.size(), used_items);
                        //int amount = (int) Math.floor((used_items.size() + 2) / 3);
                        Entity AppData = dao.lookupEntity(CommonTypes.ITEM, used_items.get(0));
                        String AppName = null;
                        if (AppData != null) {
                            AppName = AppData.maybeGet(CommonAttributes.NAME);
                        }
                        String to_append = "\"" + AppName + "\"" + cvsSplitBy;
                        Stopwatch timer2 = Stopwatch.createStarted();
                        ResultList recs = irec.recommendRelatedItemsWithDetails(LongUtils.packedSet(used_items), Integer.valueOf(getAmountRecs()), null, null);
                        timer2.stop();
                        logger.info("recommended in {}", timer2);
                        for (Result item : recs) {
                            Entity itemData = dao.lookupEntity(CommonTypes.ITEM, item.getId());
                            String name = null;
                            if (itemData != null) {
                                name = itemData.maybeGet(CommonAttributes.NAME);
                            }
                            to_append = to_append + "(\"" + name + "\"" + cvsSplitBy + String.valueOf(item.getScore()) + ")" + cvsSplitBy;
                        }
                        to_append = to_append + newLine;
                        fw.append(to_append);
                    }
                    thread_timer.stop();
                    logger.info("recommended in {}", thread_timer);*/
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    fw.flush();
                    fw.close();
                } catch (IOException e) {
                    System.out.println("Error while flushing/closing fileWriter !!!");
                    e.printStackTrace();
                }
            }
        }
    }


    // METHOD TO MAP THE NAMES RECEIVED IN TEST INPUT TO ID'
    // IN TIME CAN BE AVOIDED IF USING LENKSIT PROPERLY
    public void converter() {
        List<String> names = new ArrayList<>();
        String AppNames = "data/myData/appName.csv";
        String input_file = getTestInputFile();
        String line = "";

        // RECEBER OS NOMES DE INPUT
        try (BufferedReader br = new BufferedReader(new FileReader(input_file))) {
            while ((line = br.readLine()) != null) {
                if(line.length()!=0)
                    names.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error loading the Test Input. Please take a look at this file");
            e.printStackTrace();
            System.exit(1);
        }
        /////////////////////////////////////

        // PROCESSAR APPNAMES FILE PARA HASHMAP
        File f = new File(AppNames);
        Scanner input = null;
        try {
            input = new Scanner(f);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        Map<String,Integer> idCode = new HashMap<String,Integer>();
        String text;
        String[] str;
        while(input.hasNextLine()) {
            text = input.nextLine();
            str = text.split(cvsSplitBy);
            if (text.length() != 0) {
                String name = "\"" + str[1] + "\"";
                idCode.put(name, Integer.parseInt(str[0]));
            }
        }
        /////////////////////////////////////
        // PROCURAR NA HASHMAP PELOS NOMES E DEVOLVER ID E COLOCAR NA LISTA PARA RECOMENDAÇÃO
        for (String arg: names) {
            items = new ArrayList<>(arg.length());
            if(idCode.containsKey(arg)) {
                items.add(Long.valueOf(idCode.get(arg)));
                total_items.add(items);
            }
        }
    }
}

class MyThread extends Thread {

    private int index;
    private int thread_num;
    private String AmountRecs;
    private ItemBasedItemRecommender irec;
    private DataAccessObject dao;
    private FileWriter fw;
    private List<List<Long>> total_items;

    public MyThread(List<List<Long>> total_items,int index, int thread_num, String AmountRecs, ItemBasedItemRecommender irec, DataAccessObject dao, FileWriter fw) {
        this.index = index;
        this.thread_num = thread_num;
        this.AmountRecs = AmountRecs;
        this.irec = irec;
        this.dao = dao;
        this.fw = fw;
        this.total_items = total_items;
    }

    public void run() {
        String to_append="";
        for (int i = index; i < index + thread_num; i++) {
            List<Long> used_items = total_items.get(i);
            Entity AppData = dao.lookupEntity(CommonTypes.ITEM, used_items.get(0));
            String AppName = null;
            if (AppData != null) {
                AppName = AppData.maybeGet(CommonAttributes.NAME);
            }
            to_append = to_append + "\"" + AppName + "\"" + ",";
            ResultList recs = irec.recommendRelatedItemsWithDetails(LongUtils.packedSet(used_items), Integer.valueOf(AmountRecs), null, null);
            for (Result item : recs) {
                Entity itemData = dao.lookupEntity(CommonTypes.ITEM, item.getId());
                String name = null;
                if (itemData != null) {
                    name = itemData.maybeGet(CommonAttributes.NAME);
                }
                to_append = to_append + "(\"" + name + "\"" + "," + String.valueOf(item.getScore()) + ")" + ",";
            }
            to_append = to_append + "\n";
        }
        try {
            fw.append(to_append);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
