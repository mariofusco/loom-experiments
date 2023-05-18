package org.mfusco.loom.experiments;

import java.util.concurrent.locks.LockSupport;

public class PinningMain {

    public static void main(String[] args) {
        Object monitor = new Object();

        Thread myThread = Thread.startVirtualThread(() -> {
            synchronized (monitor) {
                LockSupport.park();
            }
        });

        try {
            Thread.sleep(1000L);
            LockSupport.unpark(myThread);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
