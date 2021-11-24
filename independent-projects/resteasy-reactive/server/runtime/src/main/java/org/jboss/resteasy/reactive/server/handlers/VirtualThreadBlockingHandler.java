package org.jboss.resteasy.reactive.server.handlers;

import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.jboss.resteasy.reactive.server.core.BlockingOperationSupport;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.spi.ServerRestHandler;

public class VirtualThreadBlockingHandler implements ServerRestHandler {

    private static volatile Executor executor;
    private static Supplier<Executor> supplier = null;

    public VirtualThreadBlockingHandler(Supplier<Executor> supplier) {
        if (VirtualThreadBlockingHandler.supplier == null) {
            VirtualThreadBlockingHandler.supplier = supplier;
        }
    }

    @Override
    public void handle(ResteasyReactiveRequestContext requestContext) throws Exception {
        //        System.out.println("BlockingHandler : " + Thread.currentThread());
        if (BlockingOperationSupport.isBlockingAllowed()) {
            return; //already dispatched
        }
        if (executor == null) {
            executor = supplier.get();
        }
        requestContext.suspend();
        requestContext.resume(executor);
    }
}
