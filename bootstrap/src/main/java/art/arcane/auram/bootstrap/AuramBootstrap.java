package art.arcane.auram.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.GLFW_FOCUSED;

/** Applies the name before GLFW creates even Forge's first loading window. */
public final class AuramBootstrap implements ClassFileTransformer {
    private final String name;

    public AuramBootstrap(String name) { this.name = name; }

    public static void premain(String arguments, Instrumentation instrumentation) {
        String name = System.getProperty("auram.branding.name", "Auram");
        instrumentation.addTransformer(new AuramBootstrap(name));
        System.out.println("[Auram Bootstrap] Startup branding enabled: " + name);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                            ProtectionDomain domain, byte[] bytes) {
        if (!"org/lwjgl/glfw/GLFW".equals(className)) return null;
        return brand(bytes);
    }

    byte[] brand(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                if (Boolean.getBoolean("auram.smoke.hiddenWindow") &&
                        (method.equals("glfwShowWindow") || method.equals("glfwFocusWindow")) && descriptor.equals("(J)V")) {
                    delegate.visitCode();
                    delegate.visitInsn(Opcodes.RETURN);
                    delegate.visitMaxs(0, 2);
                    delegate.visitEnd();
                    return null;
                }
                boolean create = method.equals("glfwCreateWindow") && descriptor.equals("(IILjava/lang/CharSequence;JJ)J");
                boolean title = method.equals("glfwSetWindowTitle") && descriptor.equals("(JLjava/lang/CharSequence;)V");
                if (!create && !title) return delegate;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        // Only JDK instructions/references are injected: no module reads,
                        // Forge class loader dependencies, or native/AWT initialization.
                        visitLdcInsn(name);
                        visitVarInsn(Opcodes.ASTORE, 2);
                        visitLdcInsn(create ? "auram.branding.createdTitle" : "auram.branding.updatedTitle");
                        visitLdcInsn(name);
                        visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        visitInsn(Opcodes.POP);
                        if (create && Boolean.getBoolean("auram.smoke.hiddenWindow")) {
                            visitLdcInsn(GLFW_VISIBLE);
                            visitInsn(Opcodes.ICONST_0);
                            visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/glfw/GLFW", "glfwWindowHint", "(II)V", false);
                            visitLdcInsn(GLFW_FOCUSED);
                            visitInsn(Opcodes.ICONST_0);
                            visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/glfw/GLFW", "glfwWindowHint", "(II)V", false);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
