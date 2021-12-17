package io.quarkus.vertx.loom.adaptor;

import java.io.IOException;
import java.io.PrintWriter;

import org.jboss.logging.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import io.quarkus.builder.item.EmptyBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.util.IoUtil;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.vertx.core.*;

import static org.objectweb.asm.Opcodes.*;

public class VertxLoomAdaptorProcessor {
    static Logger LOG = Logger.getLogger(VertxLoomAdaptorProcessor.class);

    @BuildStep
    public FeatureBuildItem feature() {
        try {
            var method = io.vertx.core.impl.ContextInternal.class.getDeclaredMethod("current");
            LOG.warn(method);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return new FeatureBuildItem("vert.x-Loom-adaptor");
    }

    @Consume(CoreVertxBuildItem.class)
    @Produce(EmptyBuildItem.class)
    @BuildStep
    void adaptVertx(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass, CoreVertxBuildItem item) throws IOException {
        LOG.info("\n\nhey I am here with " + item);

        String className = "io.vertx.core.impl.ContextInternal";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var classStream = IoUtil.readClass(cl, className);

        try {
            ClassReader cr = new ClassReader(classStream);
            ClassWriter cw = new ClassWriter(Gizmo.ASM_API_VERSION);
            VertxCurrentAdaptor adaptor = new VertxCurrentAdaptor(Gizmo.ASM_API_VERSION, cw);
            cr.accept(adaptor, 0);
            var finalClass = cw.toByteArray();
            TraceClassVisitor cv = new TraceClassVisitor(new PrintWriter(System.out));
            cr = new ClassReader(finalClass);
            cr.accept(cv, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class VertxCurrentAdaptor extends ClassVisitor {
        public VertxCurrentAdaptor() {
            this(Opcodes.ASM9);
        }

        public VertxCurrentAdaptor(int version) {
            this(version, null);
        }

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
            System.out.println(name);
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

    private class CurrentThreadMethodAdaptor extends MethodVisitor{

        public CurrentThreadMethodAdaptor(int api) {
            super(api);
        }

        public CurrentThreadMethodAdaptor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitCode() {
            mv.visitCode();
        }
        @Override
        public void visitInsn(int opcode) {
//            if ((opcode >= IRETURN && opcode <= RETURN) || opcode == ATHROW) {
//                mv.visitFieldInsn(GETSTATIC, owner, "timer", "J");
//                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System",
//                        "currentTimeMillis", "()J", false);
//                mv.visitInsn(LADD);
//                mv.visitFieldInsn(PUTSTATIC, owner, "timer", "J");
//            }
//            mv.visitInsn(opcode);

            if (mv != null) {
                mv.visitInsn(opcode);
            }
        }
        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mv.visitMaxs(maxStack + 4, maxLocals);
        }

        @Override
        public void visitLabel(final Label label) {
            if(label.info)
            if (mv != null) {
                mv.visitLabel(label);
            }
        }


    }

    private class ClassPrinter extends ClassVisitor {
        public ClassPrinter() {
            this(Opcodes.ASM9);
        }

        public ClassPrinter(int version) {
            super(version);
        }

        public ClassPrinter(int version, ClassWriter cw) {
            super(version, cw);
        }

        public ClassPrinter(int version, TraceClassVisitor tcv) {
            super(version, tcv);
        }

        public void visit(int version, int access, String name,
                String signature, String superName, String[] interfaces) {
            System.out.println(
                    name + " implements " + (interfaces.length > 0 ? interfaces[0] : "") + " extends " + superName + " {");
        }

        public void visitSource(String source, String debug) {
            System.out.println("Source : " + source);
        }

        public void visitOuterClass(String owner, String name, String desc) {
        }

        public AnnotationVisitor visitAnnotation(String desc,
                boolean visible) {
            return null;
        }

        public void visitAttribute(Attribute attr) {
        }

        public void visitInnerClass(String name, String outerName,
                String innerName, int access) {
        }

        public FieldVisitor visitField(int access, String name, String desc,
                String signature, Object value) {
            System.out.println(" " + desc + " " + name);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name,
                String desc, String signature, String[] exceptions) {
            System.out.println(" " + name + desc);
            return null;
        }

        public void visitEnd() {
            System.out.println("}");
        }
    }

}
