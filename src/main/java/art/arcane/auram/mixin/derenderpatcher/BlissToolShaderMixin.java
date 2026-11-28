package art.arcane.auram.mixin.derenderpatcher;

import art.arcane.auram.draconic.client.BlissToolCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.Program;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "codechicken.lib.render.shader.CCShaderInstance", remap = false)
abstract class BlissToolShaderMixin {
    @WrapOperation(method = "compileProgram", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL20;glShaderSource(ILjava/lang/CharSequence;)V"))
    private void auram$addDepthOutput(int shader, CharSequence source, Operation<Void> upload,
                                      ResourceProvider resources, Program.Type type, ResourceLocation name) {
        if (type == Program.Type.FRAGMENT && BlissToolCapture.isToolShader(name.toString())) {
            String fragment = source.toString();
            if (fragment.startsWith("#version 150") && fragment.contains("out vec4 fragColor;")
                    && fragment.contains("void main()")) {
                source = fragment.replace("#version 150", "#version 150\n#extension GL_ARB_explicit_attrib_location : require\n")
                        .replace("out vec4 fragColor;", """
                                layout(location = 0) out vec4 fragColor;
                                layout(location = 1) out uint auramToolDepth;
                                """)
                        .replace("void main()", "void auramToolMain()") + """

                        void main() {
                            auramToolMain();
                            // Integer attachments bypass the tool's color blending.
                            auramToolDepth = uint(gl_FragCoord.z * 16777214.0) + 1u;
                        }
                        """;
            }
        }
        upload.call(shader, source);
    }
}
