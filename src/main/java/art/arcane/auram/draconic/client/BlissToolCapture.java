package art.arcane.auram.draconic.client;

import org.joml.Matrix4f;

public interface BlissToolCapture {
    boolean auram$bindToolTarget();

    Matrix4f auram$toolProjection(Matrix4f projection);

    static boolean isToolShader(String name) {
        return switch (name) {
            case "draconicevolution:tools/tool_base", "draconicevolution:tools/tool_gem",
                    "draconicevolution:tools/tool_trace", "draconicevolution:tools/tool_blade",
                    "draconicevolution:tools/bow_string", "brandonscore:chaos_entity" -> true;
            default -> false;
        };
    }
}
