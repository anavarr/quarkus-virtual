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

        var mcl = Thread.currentThread().getContextClassLoader();
        var contexInternalClass = mcl
                .getResourceAsStream("io.vertx.core.impl.ContextInternal".replace('.', '/') + ".class");
        try {
            ClassReader cr = new ClassReader(contexInternalClass);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            VertxCurrentAdaptorPrinter adaptor = new VertxCurrentAdaptorPrinter(ASM9, cw);
            cr.accept(adaptor, 0);
            var finalClass = cw.toByteArray();
            TraceClassVisitor cv = new TraceClassVisitor(new PrintWriter(System.out));
            cr = new ClassReader(finalClass);
            cr.accept(cv, 0);
        } catch (IOException e) {
            System.out.println("there was a problem");
            e.printStackTrace();
        }

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
                    mv.visitMaxs(3, 1);
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
                    mv.visitMaxs(3, 3);
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
        Label lCatch = new Label();
        Label lVirtual = new Label();
        Label lNotVirtual = new Label();
        boolean firstReturn = true;

        public CurrentThreadMethodAdaptor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitCode() {
            mv.visitCode();
        }

        @Override
        public void visitInsn(final int opcode) {
            mv.visitInsn(opcode);
            if (opcode == ARETURN && firstReturn) {
                firstReturn = false;
                mv.visitLabel(lElse);

                //...Thread.class.getMethod("isVirtual")...
                mv.visitLdcInsn(Type.getObjectType("java/lang/Thread"));
                mv.visitLdcInsn("isVirtual");
                mv.visitInsn(ICONST_0);
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethod",
                        "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);

                //Thread.class.getMethod("isVirtual").invoke(Thread.currentThread())
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread",
                        "()Ljava/lang/Thread;", false);
                mv.visitInsn(ICONST_0);
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                        "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);

                //(Boolean)Thread.class.getMethod("isVirtual").invoke(Thread.currentThread())
                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue",
                        "()Z", false);

                //if( (Boolean)Thread.class.getMethod("isVirtual").invoke(Thread.currentThread()) )
                mv.visitJumpInsn(IFEQ, lNotVirtual); //<-- bug is
                mv.visitLabel(lVirtual);

                //if( (Boolean)Thread.class.getMehtod("isVirtual").invoke(Thread.currentTHread())
                //      && Thread.currentThread().toString().contains("vert.x-eventloop-thread-"))
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread",
                        "()Ljava/lang/Thread;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "toString",
                        "()Ljava/lang/String;", false);
                mv.visitLdcInsn("vert.x-eventloop-thread-");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains",
                        "(Ljava/lang/CharSequence;)Z", false);
                mv.visitJumpInsn(IFEQ, lNotVirtual);

                //in if
                mv.visitLdcInsn(Type.getObjectType("java/lang/Thread"));
                mv.visitLdcInsn("currentCarrierThread");
                mv.visitInsn(ICONST_0);
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod",
                        "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                mv.visitVarInsn(ASTORE, 1);

                mv.visitLabel(new Label());
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ICONST_1);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "setAccessible",
                        "(Z)V", false);

                mv.visitLabel(new Label());
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread",
                        "()Ljava/lang/Thread;", false);
                mv.visitInsn(ICONST_0);
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                        "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                mv.visitTypeInsn(CHECKCAST, "java/lang/Thread");
                mv.visitVarInsn(ASTORE, 2);

                mv.visitLabel(new Label());
                mv.visitVarInsn(ALOAD, 2);
                mv.visitTypeInsn(CHECKCAST, "io/vertx/core/impl/VertxThread");
                mv.visitMethodInsn(INVOKEVIRTUAL, "io/vertx/core/impl/VertxThread", "context",
                        "()Lio/vertx/core/impl/ContextInternal;", false);
                mv.visitInsn(ARETURN);

                mv.visitLabel(lNotVirtual);
                mv.visitInsn(ACONST_NULL);
                mv.visitInsn(ARETURN);
                //                addThrowInsn("java/lang/IllegalAccessException", mv);
                mv.visitLabel(lCatch);

                mv.visitTryCatchBlock(lElse, lCatch, lCatch, "java/lang/IllegalAccessException");
                mv.visitTryCatchBlock(lElse, lCatch, lCatch, "java/lang/reflect/InvocationTargetException");
                mv.visitTryCatchBlock(lElse, lCatch, lCatch, "java/lang/NoSuchMethodException");
            }
        }

        @Override
        public void visitJumpInsn(final int opcode, final Label label) {
            if (opcode == IFEQ && label != lNotVirtual) {
                System.out.println("We encountered an IFEQ ins");
                System.out.println("in IFEQ");
                mv.visitJumpInsn(opcode, lElse);
                //                mv.visitLabel(lElse);
            } else {
                mv.visitJumpInsn(opcode, label);
            }
        }
    }
}
