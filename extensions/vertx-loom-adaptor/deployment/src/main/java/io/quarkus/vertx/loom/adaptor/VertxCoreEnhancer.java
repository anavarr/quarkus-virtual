package io.quarkus.vertx.loom.adaptor;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.objectweb.asm.ClassVisitor;

public final class VertxCoreEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    @Override
    public ClassVisitor apply(String s, ClassVisitor classVisitor) {
        return null;
    }

    @Override
    public <V> BiFunction<String, ClassVisitor, V> andThen(Function<? super ClassVisitor, ? extends V> after) {
        return BiFunction.super.andThen(after);
    }

}
