package com.ankur.design.lld.tddkata.order;

import java.io.InputStream;
import java.io.OutputStream;

public interface OrderStreamProcessor {
    void process(InputStream in, OutputStream out);
}