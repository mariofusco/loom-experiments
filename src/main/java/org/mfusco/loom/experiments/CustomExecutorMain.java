package org.mfusco.loom.experiments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.mfusco.loom.experiments.VirtualThreadUtil.createVirtualThreadFactory;

public class CustomExecutorMain {

    private static final boolean USE_FORK_JOIN = false;

    public static void main(String[] args) throws Exception {
        // Create a virtual executor for running virtual threads
        ExecutorService executor = newExecutor();

        int amount = 10;

        // Start the echo server on a virtual thread
        executor.execute(() -> {
            try (ServerSocket serverSocket = new ServerSocket(5000)) {
                System.out.println("Echo server is running...");

                Socket socket = serverSocket.accept(); // Poller.readPoller() -> LockSupport.park()
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                    String line;
                    for (int i = 0; i < amount; i++) {
                        while ((line = reader.readLine()) != null) { // Poller.readPoller() -> LockSupport.park()
                            System.out.println("Received: " + line + " on " + Thread.currentThread());
                            writer.println(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Start the client on a virtual thread
        executor.execute(() -> {
            try (Socket socket = new Socket("localhost", 5000)) { // Poller.writePoller() -> LockSupport.park()
                System.out.println("Connected to echo server...");

                // Send random sequence of numbers
                Random rand = new Random();
                for (int i = 0; i < amount; i++) {
                    int num = rand.nextInt(100);
                    System.out.println("Sending: " + num);
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    writer.println(num);
                }

                // Wait for all the numbers to be echoed back
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                for (int i = 0; i < amount; i++) {
                    String line = reader.readLine(); // Poller.readPoller() -> LockSupport.park()
                    System.out.println("Echoed: " + line + " on " + Thread.currentThread());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread.sleep(100);

        // Shutdown the executor after both the server and client have finished
        executor.awaitTermination(10, TimeUnit.SECONDS);
        executor.close();
        System.out.println("Terminated");
    }

    private static ExecutorService newExecutor() {
        if (USE_FORK_JOIN) {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        return new VThreadsExecutor();
    }

    static class VThreadsExecutor extends AbstractExecutorService {

        private final ThreadFactory internalThreadFactory;

        private final BlockingQueue<Runnable> tasks = new LinkedTransferQueue<>();

        private final ExecutorService executor;

        private volatile boolean terminated = false;
        private volatile boolean shutdown = false;

        VThreadsExecutor() {
            this(Runtime.getRuntime().availableProcessors());
        }

        VThreadsExecutor(int nThreads) {
            this.executor = createDaemonExecutor(nThreads);
            this.internalThreadFactory = createVirtualThreadFactory(this::enqueueTask, "My Virtual Thread");
        }

        private ExecutorService createDaemonExecutor(int nThreads) {
            ExecutorService executor = Executors.newFixedThreadPool(nThreads, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            for (int i = 0; i < nThreads; i++) {
                executor.execute(this::executeTask);
            }
            return executor;
        }

        @Override
        public void execute(Runnable task) throws RejectedExecutionException {
            final Thread thread = internalThreadFactory.newThread(task);
            if (thread == null) {
                throw new RejectedExecutionException("The executor cannot accept tasks");
            }
            thread.start();
        }

        private void executeTask() {
            while (!terminated) {
                try {
                    tasks.take().run();
                } catch (InterruptedException e) {
                    terminate();
                }
            }
        }

        private void enqueueTask(Runnable runnable) {
            if (shutdown) {
                return;
            }
            try {
                tasks.put(runnable);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            List<Runnable> outstandingTasks = new ArrayList<>(tasks);
            tasks.clear();
            return outstandingTasks;
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            shutdown = true;
            try {
                if (tasks.isEmpty()) {
                    return true;
                }

                long termination = System.currentTimeMillis() + unit.toMillis(timeout);
                while (System.currentTimeMillis() < termination) {
                    Thread.sleep(1L);
                    if (tasks.isEmpty()) {
                        return true;
                    }
                }

                return false;
            } finally {
                terminate();
            }
        }

        private void terminate() {
            terminated = true;
            executor.shutdownNow();
        }
    }
}