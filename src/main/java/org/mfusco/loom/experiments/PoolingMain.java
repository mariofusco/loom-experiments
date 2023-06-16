package org.mfusco.loom.experiments;

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
    private static final boolean USE_VIRTUAL_THREADS = false;

    private static final int PARALLEL_TASK = 1_000;

    private static final ThreadLocal<ExpensiveResource> resourcePool = new ThreadLocal<>().withInitial(ExpensiveResource::new);

    public static void main(String[] args) {
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
//        return new ExpensiveResource().increment(id);
        return resourcePool.get().increment(id);
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
