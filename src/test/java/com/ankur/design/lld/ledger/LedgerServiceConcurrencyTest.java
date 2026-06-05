package com.ankur.design.lld.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LedgerServiceConcurrencyTest {
    LedgerService service ;

    @BeforeEach
    void setUp(){
        service = new LedgerService();
    }

    @Test
    void concurrentTransfers_preserveTotalBalance() throws InterruptedException {
        // arrange: fund A with 1000
        long a = service.createAccount(Currency.getInstance("USD"));
        long b   = service.createAccount(Currency.getInstance("USD"));
        service.deposit(a, new BigDecimal("1000"));

        // act: 10 threads all transferring concurrently
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch done = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                service.transferFunds(a, b, new BigDecimal("10"),Currency.getInstance("USD"));
                done.countDown();
            });
        }
        done.await();

        // assert: total is conserved
        BigDecimal total = service.getBalance(a).add(service.getBalance(b));
        assertEquals(0, total.compareTo(new BigDecimal("1000")));
    }

}
