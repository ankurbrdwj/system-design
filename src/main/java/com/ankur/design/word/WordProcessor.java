package com.ankur.design.word;//hit this is ankur

//Write a Java Program - Input is a Sentence with a lot of words in it, output should be display all the words with their frequency count
// in the descending order or counts

//Input : Ankur is talking to Siva Siva asking questions Siva likes to deep dive deep deep deep dive dive
//Output deep 4 , dive 3, Siva 3, to 2, Ankur 1, is 1

// break sentence into array of words
// iterate words array
// HashMap of frequencies = ifcontains(ith word) then increrse the frequency
//On time map build up
import java.util.*;

public class WordProcessor {

    // O(n) — bucket sort by frequency, no comparison sort needed
    List<String> wordFrequency(String sentence) {
        String[] words = sentence.split(" ");

        // step 1: count — O(n)
        Map<String, Integer> freq = new HashMap<>();
        for (String word : words) {
            freq.merge(word, 1, Integer::sum);
        }

        // step 2: bucket by frequency — index = frequency, value = words with that freq
        // max possible frequency = total word count
        @SuppressWarnings("unchecked")
        List<String>[] buckets = new List[words.length + 1];
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            int f = e.getValue();
            if (buckets[f] == null) buckets[f] = new ArrayList<>();
            buckets[f].add(e.getKey());
        }

        // step 3: walk buckets high → low — O(n)
        List<String> result = new ArrayList<>();
        for (int f = buckets.length - 1; f >= 1; f--) {
            if (buckets[f] != null) {
                for (String word : buckets[f]) {
                    result.add(word + " " + f);
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        WordProcessor processor = new WordProcessor();
        String input = "Ankur is talking to Siva Siva asking questions Siva likes to deep dive deep deep deep dive dive";
        processor.wordFrequency(input).forEach(System.out::println);
    }
}
