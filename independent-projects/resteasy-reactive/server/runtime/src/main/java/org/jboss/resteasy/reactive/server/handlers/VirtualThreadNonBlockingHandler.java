package org.jboss.resteasy.reactive.server.handlers;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.jboss.resteasy.reactive.server.core.BlockingOperationSupport;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.spi.ServerRestHandler;

public class VirtualThreadNonBlockingHandler implements ServerRestHandler {
    private Executor executor;
    private static volatile ConcurrentHashMap<String, Executor> eventLoops = new ConcurrentHashMap<>();

    public VirtualThreadNonBlockingHandler() {
    }

    @Override
    public void handle(ResteasyReactiveRequestContext requestContext) throws Exception {
        if (BlockingOperationSupport.isBlockingAllowed()) {
            return; //already dispatched
        }

        //        System.out.println("NonBlockingHandler : " + Thread.currentThread());
        if (!eventLoops.containsKey(Thread.currentThread().toString())) {
            var cs = Arrays
                    .stream(Class.forName("java.lang.ThreadBuilders")
                            .getDeclaredClasses())
                    .filter(classs -> classs.toString().contains("VirtualThreadFactory")).collect(Collectors.toList());
            var constructor = cs.get(0).getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            ThreadFactory tf = (ThreadFactory) constructor.newInstance(
                    new Object[] { requestContext.getContextExecutor(), "quarkus-virtual-factory", 0, 0,
                            null });
            var exec = (Executor) Executors.class.getMethod("newThreadPerTaskExecutor", ThreadFactory.class)
                    .invoke(this, tf);
            eventLoops.put(Thread.currentThread().toString(), exec);
        }
        requestContext.suspend();
        requestContext.resume(eventLoops.get(Thread.currentThread().toString()));
    }
}
