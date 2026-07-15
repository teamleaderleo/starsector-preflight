package dev.starsector.preflight.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Exact-version rewrite that wraps one private Starsector image-decoder method. */
final class PreparedImageTransformation {
    static final String PLAN_ID = "prepared-image-v1";
    static final String METHOD_NAME = "Ô00000";
    static final String METHOD_DESCRIPTOR = "(Ljava/lang/String;)Ljava/awt/image/BufferedImage;";
    private static final String RENAMED_METHOD = METHOD_NAME + "$preflight$original";
    private static final String BRIDGE = "dev/starsector/preflight/agent/PreparedImageBridge";

    private PreparedImageTransformation() {
    }

    static byte[] transform(AdapterTarget target, ClassSignature signature, byte[] originalBytes) {
        if (!target.planId().equals(PLAN_ID)
                || !signature.internalName().equals(target.internalClassName())
                || !signature.hasMethod(METHOD_NAME, METHOD_DESCRIPTOR)
                || target.requiredMethods().stream().noneMatch(method ->
                        method.name().equals(METHOD_NAME) && method.descriptor().equals(METHOD_DESCRIPTOR))) {
            return null;
        }

        ClassReader reader = new ClassReader(originalBytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        WrappingVisitor visitor = new WrappingVisitor(writer, signature.internalName());
        reader.accept(visitor, 0);
        if (!visitor.rewritten || visitor.conflict) {
            return null;
        }
        return writer.toByteArray();
    }

    private static final class WrappingVisitor extends ClassVisitor {
        private final String owner;
        private boolean rewritten;
        private boolean conflict;
        private int access;
        private String signature;
        private String[] exceptions;

        private WrappingVisitor(ClassVisitor delegate, String owner) {
            super(Opcodes.ASM9, delegate);
            this.owner = owner;
        }

        @Override
        public MethodVisitor visitMethod(
                int methodAccess,
                String name,
                String descriptor,
                String methodSignature,
                String[] methodExceptions) {
            if (name.equals(RENAMED_METHOD) && descriptor.equals(METHOD_DESCRIPTOR)) {
                conflict = true;
            }
            if (!name.equals(METHOD_NAME) || !descriptor.equals(METHOD_DESCRIPTOR)) {
                return super.visitMethod(methodAccess, name, descriptor, methodSignature, methodExceptions);
            }
            if (rewritten || methodAccess != Opcodes.ACC_PRIVATE) {
                conflict = true;
                return super.visitMethod(methodAccess, name, descriptor, methodSignature, methodExceptions);
            }

            rewritten = true;
            access = methodAccess;
            signature = methodSignature;
            exceptions = methodExceptions == null ? null : methodExceptions.clone();
            MethodVisitor renamed = super.visitMethod(
                    methodAccess, RENAMED_METHOD, descriptor, methodSignature, methodExceptions);
            return new MethodVisitor(Opcodes.ASM9, renamed) {
                @Override
                public void visitMethodInsn(
                        int opcode,
                        String invokedOwner,
                        String invokedName,
                        String invokedDescriptor,
                        boolean isInterface) {
                    if (invokedOwner.equals(owner)
                            && invokedName.equals(METHOD_NAME)
                            && invokedDescriptor.equals(METHOD_DESCRIPTOR)) {
                        invokedName = RENAMED_METHOD;
                    }
                    super.visitMethodInsn(opcode, invokedOwner, invokedName, invokedDescriptor, isInterface);
                }
            };
        }

        @Override
        public void visitEnd() {
            if (rewritten && !conflict) {
                MethodVisitor wrapper = super.visitMethod(
                        access, METHOD_NAME, METHOD_DESCRIPTOR, signature, exceptions);
                wrapper.visitCode();
                wrapper.visitVarInsn(Opcodes.ALOAD, 1);
                wrapper.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        BRIDGE,
                        "lookup",
                        METHOD_DESCRIPTOR,
                        false);
                wrapper.visitInsn(Opcodes.DUP);
                Label fallback = new Label();
                wrapper.visitJumpInsn(Opcodes.IFNULL, fallback);
                wrapper.visitInsn(Opcodes.ARETURN);
                wrapper.visitLabel(fallback);
                wrapper.visitFrame(
                        Opcodes.F_SAME1,
                        0,
                        null,
                        1,
                        new Object[] {"java/awt/image/BufferedImage"});
                wrapper.visitInsn(Opcodes.POP);
                wrapper.visitVarInsn(Opcodes.ALOAD, 0);
                wrapper.visitVarInsn(Opcodes.ALOAD, 1);
                wrapper.visitMethodInsn(
                        Opcodes.INVOKESPECIAL,
                        owner,
                        RENAMED_METHOD,
                        METHOD_DESCRIPTOR,
                        false);
                wrapper.visitInsn(Opcodes.ARETURN);
                wrapper.visitMaxs(2, 2);
                wrapper.visitEnd();
            }
            super.visitEnd();
        }
    }
}
