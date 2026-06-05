package com.ankur.design.lld.ledger;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.concurrent.locks.ReentrantLock;

public class Account {
    final ReentrantLock lock = new ReentrantLock();

    private  BigDecimal amount;
    private  Currency ccy;
    private final long accountId;

    public Account(BigDecimal amount, Currency ccy, long accountId) {
        this.amount = amount;
        this.ccy = ccy;
        this.accountId = accountId;
    }
    public Account(long accountId) {
        this.amount = BigDecimal.ZERO;
        this.ccy = Currency.getInstance("USD");
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCcy() {
        return ccy;
    }

    public long getAccountId() {
        return accountId;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCcy(Currency ccy) {
        this.ccy = ccy;
    }
}
