import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.JSONArray;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;


import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.io.*;
import javax.swing.table.*;


import static org.apache.spark.sql.functions.*;

public class Aggregation {

    public static void main(String[] args) throws Exception {



        //Create Session
        SparkSession spark = SparkSession
                .builder()
                .appName("Detecting Fraud Clicks")
                .master("local")
                .getOrCreate();
        
        // Aggregation
        Aggregation agg = new Aggregation();
        
        Dataset<Row> dataset = agg.loadCSVDataSet("./train_sample.csv", spark);
        dataset = agg.changeTimestempToLong(dataset);
        dataset = agg.averageValidClickCount(dataset);
        dataset = agg.clickTimeDelta(dataset);
        dataset = agg.countClickInTenMinutes(dataset);

        List<String> stringDataset = dataset.toJSON().collectAsList();
        GUI gui = new GUI(stringDataset);




    }
        
        
    private Dataset<Row> loadCSVDataSet(String path, SparkSession spark){
        // Read SCV to DataSet
        return spark.read().format("csv")
                .option("inferSchema", "true")
                .option("header", "true")
                .load(path);
    }
    
    private Dataset<Row> changeTimestempToLong(Dataset<Row> dataset){
        // cast timestamp to long
        Dataset<Row> newDF = dataset.withColumn("utc_click_time", dataset.col("click_time").cast("long"));
        newDF = newDF.withColumn("utc_attributed_time", dataset.col("attributed_time").cast("long"));
        newDF = newDF.drop("click_time").drop("attributed_time");
        return newDF;
    }
         
    private Dataset<Row> averageValidClickCount(Dataset<Row> dataset){
        // set Window partition by 'ip' and 'app' order by 'utc_click_time' select rows between 1st row to current row
        WindowSpec w = Window.partitionBy("ip", "app")
                .orderBy("utc_click_time")
                .rowsBetween(Window.unboundedPreceding(), Window.currentRow());

        // aggregation
        Dataset<Row> newDF = dataset.withColumn("cum_count_click", count("utc_click_time").over(w));
        newDF = newDF.withColumn("cum_sum_attributed", sum("is_attributed").over(w));
        newDF = newDF.withColumn("avg_valid_click_count", col("cum_sum_attributed").divide(col("cum_count_click")));
        newDF = newDF.drop("cum_count_click", "cum_sum_attributed");
        return newDF;
    }

    private Dataset<Row> clickTimeDelta(Dataset<Row> dataset){
        WindowSpec w = Window.partitionBy ("ip")
                .orderBy("utc_click_time");

        Dataset<Row> newDF = dataset.withColumn("lag(utc_click_time)", lag("utc_click_time",1).over(w));
        newDF = newDF.withColumn("click_time_delta", when(col("lag(utc_click_time)").isNull(),
                lit(0)).otherwise(col("utc_click_time")).minus(when(col("lag(utc_click_time)").isNull(),
                lit(0)).otherwise(col("lag(utc_click_time)"))));
        newDF = newDF.drop("lag(utc_click_time)");
        return newDF;
    }
    
    private Dataset<Row> countClickInTenMinutes(Dataset<Row> dataset){
        WindowSpec w = Window.partitionBy("ip")
                .orderBy("utc_click_time")
                .rangeBetween(Window.currentRow(),Window.currentRow()+600);

        Dataset<Row> newDF = dataset.withColumn("count_click_in_ten_mins",
                (count("utc_click_time").over(w)).minus(1));    //TODO 본인것 포함할 것인지 정해야함.
        return newDF;
    }
}
