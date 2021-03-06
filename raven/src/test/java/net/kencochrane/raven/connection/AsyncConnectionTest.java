package net.kencochrane.raven.connection;

import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class AsyncConnectionTest {
    @Tested
    private AsyncConnection asyncConnection;
    @Injectable
    private Connection mockConnection;
    @Injectable
    private ExecutorService mockExecutorService;
    @Mocked("addShutdownHook")
    private Runtime mockRuntime;

    @BeforeMethod
    public void setUp() throws Exception {
        // Reset Tested
        asyncConnection = null;
        new NonStrictExpectations() {{
            mockExecutorService.awaitTermination(anyLong, (TimeUnit) any);
            result = true;
        }};
    }

    @AfterMethod
    public void tearDown() throws Exception {
        // Reset the expectation that has been already removed.
        new NonStrictExpectations() {{
            mockExecutorService.awaitTermination(anyLong, (TimeUnit) any);
            result = true;
        }};
        // Ensure that the shutdown hooks are removed
        asyncConnection.close();
    }

    @Test
    public void verifyShutdownHookIsAdded() throws Exception {
        new AsyncConnection(mockConnection, mockExecutorService);

        new Verifications() {{
            mockRuntime.addShutdownHook((Thread) any);
        }};
    }

    @Test
    public void verifyShutdownHookSetManagedByRavenAndCloseConnection(
            @Mocked({"startManagingThread", "stopManagingThread"}) Raven mockRaven) throws Exception {
        new NonStrictExpectations() {{
            mockRuntime.addShutdownHook((Thread) any);
            result = new Delegate<Void>() {
                public void addShutdownHook(Thread thread) {
                    thread.run();
                }
            };
        }};

        new AsyncConnection(mockConnection, mockExecutorService);

        new VerificationsInOrder() {{
            Raven.startManagingThread();
            mockConnection.close();
            Raven.stopManagingThread();
        }};
    }

    @Test
    public void ensureFailingShutdownHookStopsBeingManaged(
            @Mocked({"startManagingThread", "stopManagingThread"}) Raven mockRaven) throws Exception {
        new NonStrictExpectations() {{
            mockRuntime.addShutdownHook((Thread) any);
            result = new Delegate<Void>() {
                public void addShutdownHook(Thread thread) {
                    thread.run();
                }
            };
            mockConnection.close();
            result = new RuntimeException("Close operation failed");
        }};

        new AsyncConnection(mockConnection, mockExecutorService);

        new Verifications() {{
            Raven.stopManagingThread();
        }};
    }

    @Test
    public void testCloseOperation() throws Exception {
        asyncConnection.close();

        new Verifications() {{
            mockConnection.close();
            mockExecutorService.awaitTermination(anyLong, (TimeUnit) any);
        }};
    }

    @Test
    public void testSendEventQueued(@Injectable final Event mockEvent) throws Exception {
        asyncConnection.send(mockEvent);

        new Verifications() {{
            mockExecutorService.execute((Runnable) any);
        }};
    }

    @Test
    public void testQueuedEventExecuted(@Injectable final Event mockEvent) throws Exception {
        new Expectations() {{
            mockExecutorService.execute((Runnable) any);
            result = new Delegate() {
                public void execute(Runnable runnable) {
                    runnable.run();
                }
            };
        }};

        asyncConnection.send(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }
}
