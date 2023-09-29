package org.mfusco.loom.experiments;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static org.mfusco.loom.experiments.VirtualThreadUtil.createVirtualThreadFactory;

public class LoomDeadlockMain {

    public static void main(String[] args) {
        var lock = new ReentrantLock(true);
        var lockAcquired = new CompletableFuture<Void>();
        var eventLoop = Executors.newSingleThreadExecutor();
        var vThreadFactory = createVirtualThreadFactory(eventLoop);

        eventLoop.execute(() -> {
            // I/O thread create a v thread on itself
            Thread vThread = vThreadFactory.newThread(() -> {
                eventLoop.execute(() -> {
                    doWithLock(lockAcquired, lock);
                });
                doWithLock(lockAcquired, lock);
            });
            vThread.start();
        });

        lock.lock();
        try {
            System.out.println("Lock acquired from " + Thread.currentThread());
            lockAcquired.complete(null);
            System.out.println("Awaiting both threads to try require the lock");
            while (lock.getQueueLength() != 2) {
                Thread.yield();
            }
        } finally {
            lock.unlock();
            System.out.println("Lock released from " + Thread.currentThread());
        }
    }

    private static void doWithLock(CompletableFuture<Void> lockAcquired, ReentrantLock lock) {
        lockAcquired.join();
        System.out.println("Try to acquire lock from " + Thread.currentThread());
        lock.lock();
        System.err.println("This never happens :(");
        System.exit(-1);
    }
}