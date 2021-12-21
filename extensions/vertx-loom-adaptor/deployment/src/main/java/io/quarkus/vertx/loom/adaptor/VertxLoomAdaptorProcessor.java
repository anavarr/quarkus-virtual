package io.quarkus.vertx.loom.adaptor;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.util.function.BiFunction;

import org.jboss.logging.Logger;
import org.objectweb.asm.*;

import io.quarkus.builder.item.EmptyBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;

public class VertxLoomAdaptorProcessor {
    static Logger LOG = Logger.getLogger(VertxLoomAdaptorProcessor.class);

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem("vert.x-Loom-adaptor");
    }

    @Consume(CoreVertxBuildItem.class)
    @Produce(EmptyBuildItem.class)
    @BuildStep
    void adaptVertx(CombinedIndexBuildItem combinedIndexBuildItem, BuildProducer<BytecodeTransformerBuildItem> producer)
            throws IOException {

        var klass = "io.vertx.core.impl.ContextInternal";
        producer.produce(new BytecodeTransformerBuildItem(klass, new BiFunction<String, ClassVisitor, ClassVisitor>() {
            @Override
            public ClassVisitor apply(String cls, ClassVisitor classVisitor) {
                return new VertxCurrentAdaptor(ASM9, classVisitor);
            }

        }));
    }

    private class VertxCurrentAdaptor extends ClassVisitor {
        public VertxCurrentAdaptor(int version, ClassVisitor cv) {
            super(version, cv);
        }

        @Override
        public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            System.out.println("in vertx method adaptor " + name);
            if (cv != null) {
                MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv != null && !name.equals("current")) {
                    mv = new CurrentThreadMethodAdaptor(Gizmo.ASM_API_VERSION, mv);
                }
                return mv;
            }
            return null;
        }
    }

    private class CurrentThreadMethodAdaptor extends MethodVisitor {
        public CurrentThreadMethodAdaptor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitCode() {
            mv.visitCode();
        }

        @Override
        public void visitInsn(int opcode) {
            if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitLdcInsn("inserting print before return");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                        "(Ljava/lang/String;)V", false);
            }
            mv.visitInsn(opcode);
        }
    }
}
