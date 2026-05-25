package com.ankur.design.lld.picnic;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Reads orders from input, processes them, writes results to output.
 */
public interface OrderStreamProcessor {
    void process(InputStream in, OutputStream out);
}