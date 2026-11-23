function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
    var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
    var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

    // Forge 47's Mixin cannot inject into these static interface methods.
    function intercept(methodName, hookName, seeded) {
        var descriptor = (seeded ? '(J)' : '()') + 'Lnet/minecraft/util/RandomSource;';
        return {
            target: {
                type: 'METHOD',
                class: 'net.minecraft.util.RandomSource',
                methodName: methodName,
                methodDesc: descriptor
            },
            transformer: function (method) {
                var fallback = new LabelNode();
                var prefix = new InsnList();
                if (seeded) {
                    prefix.add(new VarInsnNode(Opcodes.LLOAD, 0));
                }
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, 'art/arcane/auram/tweak/FasterRandom',
                    hookName, descriptor, false));
                prefix.add(new InsnNode(Opcodes.DUP));
                prefix.add(new JumpInsnNode(Opcodes.IFNULL, fallback));
                prefix.add(new InsnNode(Opcodes.ARETURN));
                prefix.add(fallback);
                prefix.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(prefix);
                return method;
            }
        };
    }

    return {
        seeded: intercept('m_216335_', 'seededSource', true),
        local: intercept('m_216343_', 'localSource', false),
        shared: intercept('m_216337_', 'sharedSource', false)
    };
}
