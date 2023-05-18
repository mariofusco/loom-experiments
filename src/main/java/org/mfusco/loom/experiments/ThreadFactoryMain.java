package org.mfusco.loom.experiments;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ThreadFactoryMain {

    public static void main(String[] args) {

        ThreadFactory threadFactory = Thread.ofVirtual()
                .name("MyThread-", 1)
                .uncaughtExceptionHandler((thread, exception) -> System.err.println(exception.getMessage() + " thrown by " + thread))
                .factory();

        Executors.newCachedThreadPool(threadFactory);

        threadFactory.newThread(() -> System.out.println("Hi, " +
                Thread.currentThread().getName())).start();

        threadFactory.newThread(() -> System.out.println("Hi, " +
                Thread.currentThread().getName())).start();

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
