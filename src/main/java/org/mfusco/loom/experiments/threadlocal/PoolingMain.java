package org.mfusco.loom.experiments.threadlocal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class PoolingMain {
    private static final boolean USE_VIRTUAL_THREADS = true;

    private static final int PARALLEL_TASK = 1_000;

//    private static final ThreadLocal<ExpensiveResource> resourcePool = ThreadLocal.withInitial(ExpensiveResource::new);
    private static final ThreadLocal<ExpensiveResource> resourcePool = createThreadLocal();

    static <T> ThreadLocal<T> createThreadLocal() {
        try {
            return (ThreadLocal<T>) Class.forName("jdk.internal.misc.CarrierThreadLocal").getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        return new ThreadLocal<>();
    }

    public static void main(String[] args) {
        System.out.println("using pool: " + resourcePool.getClass().getName());
        final Instant start = Instant.now();
        List<Integer> result = calculateResultsInParallel(PARALLEL_TASK, USE_VIRTUAL_THREADS);
        long duration = Duration.between(start, Instant.now()).toMillis();
        System.out.println("Done in " + duration + " msecs");
    }

    private static List<Integer> calculateResultsInParallel(int parallelTasks, boolean useVirtualThreads) {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());
        Consumer<Runnable> runner = createRunner(useVirtualThreads);
        CountDownLatch countDown = new CountDownLatch(parallelTasks);

        for (int i = 0; i < parallelTasks; i++) {
            final int id = i;

            runner.accept(() -> {
                results.add( useResource(id) );
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

    private static int useResource(int id) {
        return getPooledResource().increment(id);
    }

    private static ExpensiveResource getResource() {
        return new ExpensiveResource();
    }

    private static ExpensiveResource getPooledResource() {
        ExpensiveResource resource = resourcePool.get();
        if (resource == null) {
            resource = new ExpensiveResource();
            resourcePool.set(resource);
        }
        return resource;
    }

    private static Consumer<Runnable> createRunner(boolean useVirtualThreads) {
        if (useVirtualThreads) {
            return Thread::startVirtualThread;
        } else {
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            return executor::execute;
        }
    }

    private static final class ExpensiveResource {

        private final byte[] bytes;

        ExpensiveResource() {
            this.bytes = new byte[500_000_000];
        }

        int increment(int i) {
            return i + 1;
        }
    }
}
