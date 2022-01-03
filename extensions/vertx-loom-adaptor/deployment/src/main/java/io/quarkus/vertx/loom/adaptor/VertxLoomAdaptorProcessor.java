package io.quarkus.vertx.loom.adaptor;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.BiFunction;

import org.jboss.logging.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

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
        System.out.println();
        var klass = "io.vertx.core.impl.ContextInternal";
        producer.produce(new BytecodeTransformerBuildItem(klass, new BiFunction<String, ClassVisitor, ClassVisitor>() {
            @Override
            public ClassVisitor apply(String cls, ClassVisitor classVisitor) {
                var visitor = new VertxCurrentAdaptor(ASM9, classVisitor);

                var mcl = Thread.currentThread().getContextClassLoader();
                var contexInternalClass = mcl
                        .getResourceAsStream("io.vertx.core.impl.ContextInternal".replace('.', '/') + ".class");
                try {
                    ClassReader cr = new ClassReader(contexInternalClass);
                    ClassWriter cw = new ClassWriter(ASM9);
                    VertxCurrentAdaptorPrinter adaptor = new VertxCurrentAdaptorPrinter(ASM9, cw);
                    cr.accept(adaptor, 0);
                    var finalClass = cw.toByteArray();
                    TraceClassVisitor cv = new TraceClassVisitor(new PrintWriter(System.out));
                    cr = new ClassReader(finalClass);
                    cr.accept(cv, 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return visitor;
            }

        }));
    }

    private class VertxCurrentAdaptor extends ClassVisitor {
        public VertxCurrentAdaptor(int version, ClassVisitor cv) {
            super(version, cv);
            System.out.println("about to adapt your class");
        }

        @Override
        public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            if (cv != null) {
                MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("current")) {
                    mv = new CurrentThreadMethodAdaptor(Gizmo.ASM_API_VERSION, mv);
                    mv.visitMaxs(10, 10);
                    return mv;
                }
                return mv;
            }
            return null;
        }
    }

    private class VertxCurrentAdaptorPrinter extends ClassVisitor {
        public VertxCurrentAdaptorPrinter(int version, ClassVisitor cv) {
            super(version, cv);
            System.out.println("about to adapt your class");
        }

        @Override
        public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            if (cv != null) {
                if (name.equals("current")) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv = new CurrentThreadMethodAdaptor(Gizmo.ASM_API_VERSION, mv);
                    mv.visitMaxs(10, 10);
                    return mv;
                }
                return null;
            }
            return null;
        }
    }

    private static void addPrintInsn(String msg, MethodVisitor mv) {
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn(msg);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    }

    private static void addThrowInsn(String exceptionClass, MethodVisitor mv) {
        mv.visitTypeInsn(NEW, exceptionClass);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, exceptionClass, "<init>", "()V", false);
        mv.visitInsn(ATHROW);
    }

    private class CurrentThreadMethodAdaptor extends MethodVisitor {
        Label lElse = new Label();
        Label lTry = new Label();
        Label lCatch = new Label();
        boolean firstReturn = true;

        public CurrentThreadMethodAdaptor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitCode() {
            mv.visitCode();
        }

        //        @Override
        //        public void visitLabel(final Label label) {
        //            mv.visitLabel(label);
        //        }

        @Override
        public void visitInsn(final int opcode) {
            if (opcode == ARETURN && firstReturn) {
                addPrintInsn("You are returning", mv);
            }
            mv.visitInsn(opcode);
            if (opcode == ARETURN && firstReturn) {
                firstReturn = false;
                mv.visitLabel(lElse);
                addPrintInsn("in else", mv);
                mv.visitLabel(lTry);
                addPrintInsn("in try", mv);
                addThrowInsn("java/lang/IllegalAccessException", mv);
                mv.visitLabel(lCatch);
                addPrintInsn("in catch", mv);
                //            mv.visitLabel(lElse);
                //            addPrintInsn("in new Label()", mv);
                mv.visitTryCatchBlock(lTry, lCatch, lCatch, "java/lang/IllegalAccessException");
                //                mv.visitTryCatchBlock(lTry, lCatch, lCatch, "Ljava/lang/reflect/InvocationTargetException");
                //                mv.visitTryCatchBlock(lTry, lCatch, lCatch, "Ljava/lang/NoSuchMethodException");
            }
        }

        @Override
        public void visitJumpInsn(final int opcode, final Label label) {
            if (opcode == IFEQ) {
                System.out.println("in IFEQ");
                mv.visitJumpInsn(opcode, lElse);
                //                mv.visitLabel(lElse);
            } else {
                mv.visitJumpInsn(opcode, label);
            }
        }

        //        @Override
        //        public void visitInsn(int opcode) {
        //            if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
        //                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        //                mv.visitLdcInsn("inserting print before return");
        //                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
        //                        "(Ljava/lang/String;)V", false);
        //            }
        //            mv.visitInsn(opcode);
        //        }
    }
}
