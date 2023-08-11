package org.mfusco.loom.experiments;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Fork(1)
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Benchmark)
@Warmup(iterations = 5000)
@Measurement(iterations = 10000)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class LoomBenchmark {

    @Param({"true", "false"})
    private boolean useVirtualThreads;

    //    @Param({"100", "1000"})
    @Param({"100"})
    private int parallelTasks;

    private Consumer<Runnable> runner;

    @Setup
    public void setup() {
        this.runner = createRunner(useVirtualThreads);
    }

    @Benchmark
    public void benchmark(Blackhole bh) throws Exception {
        CountDownLatch countDown = new CountDownLatch(parallelTasks);

        for (int i = 0; i < parallelTasks; i++) {
            runner.accept(() -> {
                Blackhole.consumeCPU(100);
                countDown.countDown();
            });
        }

        try {
            countDown.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Consumer<Runnable> createRunner(boolean useVirtualThreads) {
        if (useVirtualThreads) {
            return Thread::startVirtualThread;
        } else {
            return Executors.newWorkStealingPool()::execute;
        }
    }
}


