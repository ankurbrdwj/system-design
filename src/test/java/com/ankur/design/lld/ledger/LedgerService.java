package com.ankur.design.lld.ledger;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class LedgerService {
    AtomicLong uuidLong;
    Map<Long, Account> map = new ConcurrentHashMap<>();

    public LedgerService() {
        this.uuidLong = new AtomicLong(1L);
    }

    long createAccount(Currency ccy) {
        long accountId = uuidLong.incrementAndGet();
        map.put(accountId, new Account(BigDecimal.ZERO, ccy, accountId));
        return accountId;
    }

    public BigDecimal getBalance(long accountId) {
        Account account = map.get(accountId);
        if (account == null) throw new IllegalArgumentException("Account not found: " + accountId);
        return account.getAmount();
    }

    public long deposit(long from, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be > 0");
        Account account = map.get(from);
        if (account == null) throw new IllegalArgumentException("Account not found: " + from);
        account.lock.lock();
        try {
            account.setAmount(account.getAmount().add(amount));
        } finally {
            account.lock.unlock();
        }
        return account.getAccountId();
    }

    public void transferFunds(long from, long to, BigDecimal amount, Currency usd) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be > 0");
        if (from == to) throw new IllegalArgumentException("Cannot transfer to same account");
        Account accountA = map.get(from);
        if (accountA == null) throw new IllegalArgumentException("Account not found: " + from);
        Account accountB = map.get(to);
        if (accountB == null) throw new IllegalArgumentException("Account not found: " + to);

        // consistent lock ordering by id — prevents deadlock when A→B and B→A run concurrently
        Account first  = from < to ? accountA : accountB;
        Account second = from < to ? accountB : accountA;

        first.lock.lock();
        second.lock.lock();
        try {
            if (accountA.getAmount().compareTo(amount) < 0) throw new IllegalStateException("Insufficient funds");
            accountA.setAmount(accountA.getAmount().subtract(amount));
            accountB.setAmount(accountB.getAmount().add(amount));
        } finally {
            second.lock.unlock();
            first.lock.unlock();
        }
    }
}
