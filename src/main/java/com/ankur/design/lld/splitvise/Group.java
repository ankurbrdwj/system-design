package com.ankur.design.lld.splitvise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Group {
    Map<String, List<Debt>> balances = new HashMap<>();

    public Map<String, List<Debt>> settle() {
        return balances;
    }


    public void addExpense(String name, int amount, List<String> benefeciaries) {
        int share = amount/benefeciaries.size();
        for (String benefeciary : benefeciaries) {
            if (name.equals(benefeciary)) {
                //creditors.add(new Debt(name,0));
                balances.put(benefeciary, new ArrayList<>());
            } else {
                // If previous creditors exist we need to update map ,
                // get previous or empty , then iterate creditors and add share
                // we have to merge two list ,
                balances.computeIfAbsent(benefeciary,k -> new ArrayList<>())
                        .add(new Debt(name, share));
            }
        }
    }
}
