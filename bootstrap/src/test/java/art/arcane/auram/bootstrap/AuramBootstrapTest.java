package art.arcane.auram.bootstrap;

import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class AuramBootstrapTest {
    @Test public void transformsExecutableMethodsWithoutChangingOtherArguments() throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Fixture", null, "java/lang/Object", null);
        MethodVisitor create = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "glfwCreateWindow", "(IILjava/lang/CharSequence;JJ)J", null, null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 2);
        create.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/CharSequence", "toString", "()Ljava/lang/String;", true);
        create.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J", false);
        create.visitVarInsn(Opcodes.LLOAD, 3); create.visitInsn(Opcodes.LADD);
        create.visitVarInsn(Opcodes.LLOAD, 5); create.visitInsn(Opcodes.LADD);
        create.visitInsn(Opcodes.LRETURN); create.visitMaxs(0, 0); create.visitEnd();
        MethodVisitor title = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "glfwSetWindowTitle", "(JLjava/lang/CharSequence;)V", null, null);
        title.visitCode(); title.visitLdcInsn("fixture.title"); title.visitVarInsn(Opcodes.ALOAD, 2);
        title.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/CharSequence", "toString", "()Ljava/lang/String;", true);
        title.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        title.visitInsn(Opcodes.POP); title.visitInsn(Opcodes.RETURN); title.visitMaxs(0, 0); title.visitEnd(); writer.visitEnd();
        byte[] transformed = new AuramBootstrap("42").brand(writer.toByteArray());
        Class<?> fixture = new ClassLoader() { Class<?> load() { return defineClass("Fixture", transformed, 0, transformed.length); } }.load();
        assertEquals(54L, fixture.getMethod("glfwCreateWindow", int.class, int.class, CharSequence.class, long.class, long.class).invoke(null, 800, 600, "wrong", 5L, 7L));
        fixture.getMethod("glfwSetWindowTitle", long.class, CharSequence.class).invoke(null, 1L, "wrong");
        assertEquals("42", System.getProperty("fixture.title"));
        assertNull(new AuramBootstrap("Auram").transform(null, "unrelated/Class", null, null, new byte[0]));
    }
}
