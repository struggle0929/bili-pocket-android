package cn.eveonecat.pocket;

import java.util.Random;

/** A plan is sampled once and stored by task ID, never resampled on each scheduler tick. */
record TaskTiming(int beforeSeconds,int holdSeconds) {
    static final int MAX_SECONDS=86400;
    static TaskTiming sample(int before,int beforeJitter,int hold,int holdExtra,Random random){
        before=bounded(before);hold=bounded(hold);
        int low=Math.max(0,before-bounded(beforeJitter)),high=Math.min(MAX_SECONDS,before+bounded(beforeJitter));
        int last=Math.min(MAX_SECONDS,hold+bounded(holdExtra));
        return new TaskTiming(low+random.nextInt(high-low+1),hold+random.nextInt(last-hold+1));
    }
    static int bounded(int value){return Math.max(0,Math.min(MAX_SECONDS,value));}
    long enterAt(long end){return end-beforeSeconds*1000L;}
    long leaveAt(long end){return end+holdSeconds*1000L;}
    String encoded(){return beforeSeconds+","+holdSeconds;}
    static TaskTiming decode(String value){
        try{String[] parts=value.split(",");if(parts.length!=2)return null;int before=Integer.parseInt(parts[0]),hold=Integer.parseInt(parts[1]);return before==bounded(before)&&hold==bounded(hold)?new TaskTiming(before,hold):null;}catch(RuntimeException e){return null;}
    }
}
