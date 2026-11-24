package art.arcane.auram.field.client;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING;

public final class VoxelTextureUpload {
    private VoxelTextureUpload() {}

    public static void withUnpackState(int texture, Runnable upload) {
        int bound = glGetInteger(GL_TEXTURE_BINDING_3D);
        int buffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] parameters = {GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT,
                GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_IMAGES};
        int[] previous = new int[parameters.length];
        try {
            glBindTexture(GL_TEXTURE_3D, texture);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            for (int i = 0; i < parameters.length; i++) {
                previous[i] = glGetInteger(parameters[i]);
                glPixelStorei(parameters[i], i == 0 ? 4 : 0);
            }
            upload.run();
        } finally {
            for (int i = 0; i < parameters.length; i++) glPixelStorei(parameters[i], previous[i]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, buffer);
            glBindTexture(GL_TEXTURE_3D, bound);
        }
    }
}
