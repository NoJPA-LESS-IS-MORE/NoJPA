package dk.lessismore.nojpa.utils;

import java.util.*;

/**
 * User: seb
 */
public class TimerWithPrinter {


    static HashMap<String, Longs> laps = new HashMap<String, Longs>();


    static class Longs {

        public long avg = 0;
        public long count = 0;
        public long total = 0;
        public long diffTotal = 0;
        public long diffAvg = 0;

        public Longs(long currentTime, long diff){
            avg = currentTime;
            total = currentTime;
            diffTotal = diff;
            diffAvg = diff;
            count = 1;
        }

        public Longs newLap(long lapTime, long diff){
            count++;
            total += lapTime;
            diffTotal += diff;
            avg = total / count;
            diffAvg = diffTotal / count;
            return this;
        }


        @Override
        public String toString() {
            return "avg("+ avg +") count("+ count +") total("+ total +")  ---- diffAvg("+ diffAvg +") diffTotal("+ diffTotal +")";
        }
    }



    long start = System.currentTimeMillis();
    long end = System.currentTimeMillis();
    long diff = System.currentTimeMillis();

    String prefix = null;
    String fileName = null;

    public TimerWithPrinter(String prefix, String fileName){
        this.prefix = prefix;
        this.fileName = fileName;
        start();
        //System.out.println("file = " + (new File(fileName).getAbsolutePath()));
    }

    public void tmpTime(String comment){
        String toPrint = prefix + ":" + comment + " " + (start - System.currentTimeMillis()) + "\n";
        System.out.print(toPrint);
        SuperIO.writeTextToFile(fileName, toPrint, true);
    }

    public void markLap(String lapID){
        Long thisDiff = System.currentTimeMillis() - diff;
        diff = System.currentTimeMillis();
        Long thisLap = System.currentTimeMillis() - start;
        Longs allLaps = laps.get(lapID);
        if(allLaps == null){
            laps.put(lapID, new Longs(thisLap, thisDiff));
        } else {
            laps.put(lapID, allLaps.newLap(thisLap, thisDiff));
        }
    }

    public static void printLapsInAlfaOrder(){
        Map.Entry<String,Long>[] entries = laps.entrySet().toArray(new Map.Entry[laps.size()]);
        Arrays.sort(entries, new GenericComparator<Object>(Map.Entry.class, "key"));
        System.out.println("------------------"+ new Date() +"-------------------- START printing stats");
        for(int i = 0; i < entries.length; i++){
            String s = "printLap(" + entries[i].getKey() + ") -> " + entries[i].getValue() + "\n";
            SuperIO.writeTextToFile("/tmp/printLapsInAlfaOrder.log", s, true);
            System.out.print(s);
        }
        System.out.println("------------------"+ new Date() +"-------------------- END printing stats");
    }


    public void getTime(){
        String toPrint = prefix + " " + (end- start) + "\n";
        System.out.print(toPrint);
        SuperIO.writeTextToFile(fileName, toPrint, true);
    }


    public void stop(){
        end = System.currentTimeMillis();
        getTime();
    }

    public void start(){
        start = System.currentTimeMillis();
    }


    public static void main(String[] args) throws Exception {
        TimerWithPrinter t = new TimerWithPrinter("test", "/tmp/timer.log");
        for(int i = 0; i < 3; i++){
            t.markLap("1");
            Thread.sleep(200);
            t.markLap("2");
            Thread.sleep(100);
            t.markLap("3");
            t.printLapsInAlfaOrder();
        }


    }


}
