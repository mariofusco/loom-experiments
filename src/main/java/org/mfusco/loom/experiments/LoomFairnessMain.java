package org.mfusco.loom.experiments;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class LoomFairnessMain {

    private static final boolean USE_VIRTUAL_THREADS = false;

    private static final int EXEC_NR = 8 * Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        Set<Result> results = calculateResultsInParallel(EXEC_NR, USE_VIRTUAL_THREADS);
        results.forEach(System.out::println);

        long total = results.stream().mapToLong(Result::duration).sum();
        long min = results.stream().mapToLong(Result::duration).min().getAsLong();
        long max = results.stream().mapToLong(Result::duration).max().getAsLong();

        System.out.println("min duration: " + min);
        System.out.println("max duration: " + max);
        System.out.println("average duration: " + total/EXEC_NR);
    }

    private static Set<Result> calculateResultsInParallel(int parallelTasks, boolean useVirtualThreads) {
        Set<Result> results = Collections.synchronizedSet(new TreeSet<>(Comparator.comparingInt(Result::id)));
        Consumer<Runnable> runner = createRunner(useVirtualThreads);
        CountDownLatch countDown = new CountDownLatch(parallelTasks);

        for (int i = 0; i < parallelTasks; i++) {
            final Instant start = Instant.now();
            final int id = i;

            runner.accept(() -> {
                results.add( cpuBoundTask(id, start) );
                countDown.countDown();
            });
        }

        try {
            countDown.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return results;
    }

    private static Consumer<Runnable> createRunner(boolean useVirtualThreads) {
        if (useVirtualThreads) {
            return Thread::startVirtualThread;
        } else {
            ExecutorService executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            return executor::execute;
        }
    }

    record Result(int id, long duration, BigInteger count) { }

    private static Result cpuBoundTask(int id, Instant start) {
        BigInteger count = BigInteger.ZERO;

        for(int i = 0; i < 10_000_000; i++) {
            count = count.add(BigInteger.valueOf(1L));
        }

        long duration = Duration.between(start, Instant.now()).toMillis();
        return new Result(id, duration, count);
    }
}
