package com.ankur.design.lld.splitvise;

import java.util.Objects;

public class Debt {

    private String creditor;
    private double amount ;
    public Debt(String creditor, double amount) {
        this.amount=amount;
        this.creditor=creditor;
    }

    public String getCreditor() {
        return creditor;
    }

    public void setCreditor(String creditor) {
        this.creditor = creditor;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Debt debt = (Debt) o;
        return Double.compare(amount, debt.amount) == 0 && Objects.equals(creditor, debt.creditor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creditor, amount);
    }
}
