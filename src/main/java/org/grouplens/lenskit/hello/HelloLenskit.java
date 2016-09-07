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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.common.base.Stopwatch;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Demonstration app for LensKit. This application builds an item-item CF model
 * from a CSV file, then generates recommendations for a user.
 *
 * Usage: java org.grouplens.lenskit.hello.HelloLenskit ratings.csv user
 */
public class HelloLenskit implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(HelloLenskit.class);

    /** used by logging to file code
     * global and accessibel by other files
     */

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
    public List<Long> items;
    private static List<List<Long>> total_items = new ArrayList<List<Long>>();
    private static List<String> out_names = new ArrayList<>();
    public static volatile int out_names_index = 0;
    private List<String> config_file = new ArrayList<>();
    private final String cvsSplitBy = ",";

    public HelloLenskit(String ModeInput, String ConfigInput) {
        mode = ModeInput;
        logger.info("reading aptoide config file");
        config_file = readAptoideConfigFile(ConfigInput);
        dataFile = Paths.get(getDataFile());
    }

    // Method to read aptoide config file
    // This file must have:
    //  1 - Trained Model file (either for input and output);
    //  2 - Configuration file for training;
    //  3 - Data file (.yml required);
    //  4 - Test input file;
    //  5 - Test output file;
    //  6 - Log File;
    //  7 - Number of recommendations needed per item;
    //  8 - Number of Threads for testing;

    public static List<String> readAptoideConfigFile(String ConfigInput){
        String line = "";
        List<String> config_file = new ArrayList<>();
        FileReader aptoide_config = null;
        try {
            aptoide_config = new FileReader(ConfigInput);
        } catch (IOException e) {
            System.err.println("Please insert a valid Aptoide Config File");
            e.printStackTrace();
            System.exit(1);
        }
        try (BufferedReader br = new BufferedReader(aptoide_config)) {
            while ((line = br.readLine()) != null) {
                config_file.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return config_file;
    }

    private String getModelFile(){
        return config_file.get(0);
    }

    private String getConfigFile(){
        return config_file.get(1);
    }

    private String getDataFile(){
        return config_file.get(2);
    }

    private String getAppNameFile(){
        return config_file.get(3);
    }

    private String getTestInputFile(){
        return config_file.get(4);
    }

    private String getTestOutPutFile(){
        return config_file.get(5);
    }

    private String getLogFile(){
        return config_file.get(6);
    }

    private int getAmountRecs(){
        return Integer.valueOf(config_file.get(7));
    }

    private String getNumberThreads(){
        return config_file.get(8);
    }


    public void run() {
        HeapMemoryPrinter(1);
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
            //Train doesn't need total_items nor out_names
            total_items = null;
            out_names = null;
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
            HeapMemoryPrinter(2);
        }
        else if (mode.equals("Test")){
            converter();
            BufferedWriter bufferedWriter = null;
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
                /*FileWriter fw = new FileWriter(getTestOutPutFile());
                bufferedWriter = new BufferedWriter(fw);*/
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
                        int thread_items = items_by_thread * i;
                        if (i < n_threads -1)
                            Pool[i] = new Thread(new MyThread(thread_items, items_by_thread, getAmountRecs(),
                                    total_items, out_names, irec, dao, bufferedWriter, i));
                        else {
                            int items_by_thread_x = (total_items.size() - ((n_threads - 1) * items_by_thread));
                            Pool[i] = new Thread(new MyThread(thread_items, items_by_thread_x, getAmountRecs(),
                                    total_items, out_names, irec, dao, bufferedWriter, i));
                        }
                        Pool[i].start();
                    }
                    logger.info("Threads Running");
                    for (int j = 0; j < n_threads; j++) {
                        Pool[j].join();
                        Pool[j] = null;
                    }
                    thread_timer.stop();
                    logger.info("recommended in {}", thread_timer);
                    HeapMemoryPrinter(3);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    bufferedWriter.flush();
                    bufferedWriter.close();
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
        String AppNames = getAppNameFile();
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
        int k = 0;
        int x = 0;
        for (String arg: names) {
            items = new ArrayList<>(arg.length());
            if(idCode.containsKey(arg)) {
                items.add(Long.valueOf(idCode.get(arg)));
                total_items.add(items);
            }
            else {
                items.add(0L);
                total_items.add(items);
                out_names.add(arg);
            }
        }
    }

    public void HeapMemoryPrinter(int i){
        // =============== heap memory test ===============================
        int mb = 1024*1024;
        //Getting the runtime reference from system
        Runtime runtime = Runtime.getRuntime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        FileWriter writer;
        BufferedWriter bufferedWriter;
        System.out.println("=============================================================");
        if (i == 1)
            System.out.println("##### Heap utilization statistics [MB] - run() started #####");
        if (i == 2)
            System.out.println("##### Heap utilization statistics [MB] - Train Engine Completed #####");
        if (i == 3)
            System.out.println("##### Heap utilization statistics [MB] - Test Completed #####");
        System.out.println("Used Memory:" + (runtime.totalMemory() - runtime.freeMemory()) / mb);
        System.out.println("Free Memory:" + runtime.freeMemory() / mb);
        System.out.println("Total Available Memory:" + runtime.totalMemory() / mb);
        System.out.println("Max Available Memory:" + runtime.maxMemory() / mb);
        System.out.println("=============================================================");
        // ================================================================
        // =============== log to file ====================================
        try {
            writer = new FileWriter(getLogFile(), true);
            bufferedWriter = new BufferedWriter(writer);
            bufferedWriter.write("=============================================================");
            bufferedWriter.newLine();
            if (i == 1)
                bufferedWriter.write("##### Heap utilization statistics [MB] - run() started #####");
            if (i == 2)
                bufferedWriter.write("##### Heap utilization statistics [MB] - Train Engine Completed #####");
            if (i == 3)
                bufferedWriter.write("##### Heap utilization statistics [MB] - Test Completed #####");
            bufferedWriter.newLine();
            bufferedWriter.write("Time: " + dateFormat.format(date));
            bufferedWriter.newLine();
            bufferedWriter.write("Used Memory:" + (runtime.totalMemory() - runtime.freeMemory()) / mb);
            bufferedWriter.newLine();
            bufferedWriter.write("Free Memory:" + runtime.freeMemory() / mb);
            bufferedWriter.newLine();
            bufferedWriter.write("Total Available Memory:" + runtime.totalMemory() / mb);
            bufferedWriter.newLine();
            bufferedWriter.write("Max Available Memory:" + runtime.maxMemory() / mb);
            bufferedWriter.newLine();
            bufferedWriter.flush();
            if (i == 2 || i == 3)
                bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class MyThread extends Thread {

    private final int index;
    private final int thread_num;
    private final int thread_index;
    private static volatile int AmountRecs;
    private static volatile ItemBasedItemRecommender irec;
    private static volatile DataAccessObject dao;
    private static volatile List<List<Long>> total_items;
    private static volatile List<String> out_names;
    //private BufferedWriter bufferedWriter;
    private final Object lock_if = new Object();
    private final Object lock_else = new Object();
    private final Object lock_writer = new Object();

    public MyThread(int Index, int Thread_num, int amountRecs,
                    List<List<Long>> Total_items, List<String> Out_names,
                    ItemBasedItemRecommender Irec, DataAccessObject Dao, BufferedWriter BufferedWriter, int i) {
        index = Index;
        thread_num = Thread_num;
        thread_index = i;
        AmountRecs = amountRecs;
        total_items = Total_items;
        out_names = Out_names;
        irec = Irec;
        dao = Dao;
        //bufferedWriter= BufferedWriter;
    }

    public void run() {
        BufferedWriter bufferedWriter = null;
        try {
            FileWriter fw = new FileWriter("etc/test_output" + thread_index + ".txt");
            bufferedWriter = new BufferedWriter(fw);
        }catch (Exception e){
            e.printStackTrace();
            System.exit(1);
        }
        for (int i = index; i < index + thread_num; i++) {
            List<Long> used_items = total_items.get(i);
            Entity AppData = dao.lookupEntity(CommonTypes.ITEM, used_items.get(0));
            String AppName;
            String to_append = "";
            if (AppData != null) {
                ResultList recs = irec.recommendRelatedItemsWithDetails(LongUtils.packedSet(used_items), AmountRecs, null, null);
                synchronized (lock_if) {
                    AppName = AppData.maybeGet(CommonAttributes.NAME);
                    to_append = to_append + "\"" + AppName + "\"" + ",";
                    int k = 0;
                    for (Result item : recs) {
                        k++;
                        Entity itemData = dao.lookupEntity(CommonTypes.ITEM, item.getId());
                        String name = null;
                        if (itemData != null) {
                            name = itemData.maybeGet(CommonAttributes.NAME);
                        }
                        to_append = to_append + "(\"" + name + "\"" + "," + String.valueOf(item.getScore()) + ")";
                        if (k < AmountRecs)
                            to_append = to_append + ",";
                    }
                    to_append = to_append + "\n";
                }
            }
            else {
                synchronized (lock_else) {
                    to_append = to_append + out_names.get(HelloLenskit.out_names_index) + "\n";
                    HelloLenskit.out_names_index++;
                }
            }
            try {
                synchronized (lock_writer) {
                    bufferedWriter.write(to_append);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
