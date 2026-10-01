package org.wifimotion.test;
public final class Detector {
 public double mean=0,sd=0;public int sensitivity=1;int count=0;boolean latched=false;
 public void reset(){count=0;latched=false;}
 public boolean sample(int r){double threshold=Math.max(sensitivity==0?5:sensitivity==2?2:3,sd*3+1);double delta=Math.abs(r-mean);if(delta<=1){reset();return false;}if(latched)return false;if(delta>=threshold){count++;if(count>=2){latched=true;return true;}}else count=0;return false;}
}
