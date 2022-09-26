package com.ankur.design.multithreading.square;

import com.ankur.design.multithreaded.square.SleepSort;
import org.junit.jupiter.api.Test;

public class TestSleepSort {
  @Test
  public void testSort(){
    int[] arr = new int[]{7,3,5,1,8,3,9,2,6,4};
    SleepSort.sleepSortAndPrint(arr);
  }
}
