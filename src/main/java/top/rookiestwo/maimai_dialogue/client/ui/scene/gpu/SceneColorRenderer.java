package top.rookiestwo.maimai_dialogue.client.ui.scene.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.opengl.GLTexture;
import org.lwjgl.system.MemoryStack;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.presentation.filter.ColorAdjustFilter;
import top.rookiestwo.maimai_dialogue.presentation.filter.CrtFilter;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.lwjgl.opengl.GL33C.*;

/** Shared GPU program. Invoked by ordered Arc3D tasks, never by the UI thread. */
public final class SceneColorRenderer {
    private static int program, vertexArray, framebuffer, adjustmentsLocation, tintLocation;
    private static volatile boolean hookReady;
    private static volatile boolean diagnostics;
    public static volatile double gpuMicros;
    public static volatile long samples;
    private static final int[] queries = new int[4];
    private static final boolean[] pending = new boolean[4];
    private static int cursor;
    private static String reported = "";
    private static final SceneCrtProgram crtProgram = new SceneCrtProgram();
    private SceneColorRenderer() { }

    public static void beginFrame() { hookReady = true; }
    public static void diagnostics(boolean enabled) { diagnostics = enabled; }

    static void draw(GLTexture source, GLTexture target, GLTexture bloomA, GLTexture bloomB, SceneFilter settings, float time) {
        RenderSystem.assertOnRenderThread();
        if (!hookReady) throw new IllegalStateException("ModernUI scene GPU bridge was not initialized");
        try (var saved = new GlState()) {
            if (program == 0) initialize();
            if (settings instanceof CrtFilter crt) crtProgram.prepare(crt.bloom() > 0);
            glActiveTexture(GL_TEXTURE0); glBindSampler(0, 0);
            glBindTexture(GL_TEXTURE_2D, source.getHandle());
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            try {
                glDisable(GL_DEPTH_TEST); glDepthMask(false); glDisable(GL_STENCIL_TEST);
                glDisable(GL_BLEND); glDisable(GL_CULL_FACE); glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
                glColorMask(true, true, true, true);
                glBindVertexArray(vertexArray);
                collectTimings();
                boolean measure = diagnostics && !pending[cursor] && glGetQueryi(GL_TIME_ELAPSED, GL_CURRENT_QUERY) == 0;
                if (measure) glBeginQuery(GL_TIME_ELAPSED, queries[cursor]);
                try {
                    if (settings instanceof ColorAdjustFilter color) {
                        bindTarget(target); glUseProgram(program);
                        glUniform3f(adjustmentsLocation, color.brightness(), color.contrast(), color.saturation());
                        int tint = color.tint().map(value -> value.argb()).orElse(0);
                        glUniform4f(tintLocation, (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f,
                                (tint & 255) / 255f, (tint >>> 24) / 255f);
                        glDrawArrays(GL_TRIANGLES, 0, 3);
                    } else if (settings instanceof CrtFilter crt) {
                        crtProgram.draw(source, target, bloomA, bloomB, crt, time);
                    }
                }
                finally {
                    if (measure) { glEndQuery(GL_TIME_ELAPSED); pending[cursor] = true; cursor = (cursor + 1) % queries.length; }
                }
                String mode = settings.type() + (bloomA == null ? ":1" : ":4");
                if (!reported.equals(mode)) {
                    reported = mode;
                    MaiMaiDialogue.LOGGER.info("Scene GPU filter: pipeline={}, source={}x{}, output={}x{}, bloom={}x{}, settings={}",
                            mode, source.getWidth(), source.getHeight(), target.getWidth(), target.getHeight(),
                            bloomA == null ? 0 : bloomA.getWidth(), bloomA == null ? 0 : bloomA.getHeight(), settings);
                }
            } finally {
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            }
        }
    }

    static void bindTarget(GLTexture target) {
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target.getHandle(), 0);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Scene filter framebuffer is incomplete");
        glViewport(0, 0, target.getWidth(), target.getHeight());
    }

    private static void initialize() {
        program = program("scene_color/adjust.frag");
        adjustmentsLocation = glGetUniformLocation(program, "Adjustments"); tintLocation = glGetUniformLocation(program, "Tint");
        glUseProgram(program); glUniform1i(glGetUniformLocation(program, "Scene"), 0);
        vertexArray = glGenVertexArrays(); framebuffer = glGenFramebuffers();
        for (int i = 0; i < queries.length; i++) queries[i] = glGenQueries();
        MaiMaiDialogue.LOGGER.info("Scene GPU ColorAdjust: shader compile/link OK, GL {}", glGetString(GL_VERSION));
    }

    private static void collectTimings() {
        for (int i = 0; i < queries.length; i++) {
            if (!pending[i] || glGetQueryObjecti(queries[i], GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) continue;
            double micros = glGetQueryObjectui64(queries[i], GL_QUERY_RESULT) / 1000.0;
            pending[i] = false;
            gpuMicros = samples == 0 ? micros : gpuMicros * .95 + micros * .05;
            if (++samples % 300 == 0 && diagnostics) MaiMaiDialogue.LOGGER.info("Scene GPU filter: pipeline={}, pass avg={} us, samples={}", reported, Math.round(gpuMicros), samples);
        }
    }

    /** The program is shared across scenes; large surfaces are owned and released by each SceneColorLayer. */
    public static void shutdown() {
        RenderSystem.assertOnRenderThread();
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
        if (program != 0) glDeleteProgram(program);
        crtProgram.close();
        for (int i = 0; i < queries.length; i++) { if (queries[i] != 0) glDeleteQueries(queries[i]); queries[i] = 0; pending[i] = false; }
        framebuffer = vertexArray = program = 0; hookReady = false; reported = "";
    }
    private static String source(String file) {
        try (var stream = SceneColorRenderer.class.getResourceAsStream("/assets/maimai_dialogue/shaders/" + file)) {
            if (stream == null) throw new IOException("Missing shader " + file);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    private static int shader(int type, String file) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source(file)); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader); glDeleteShader(shader);
            throw new IllegalStateException(file + ": " + error);
        }
        return shader;
    }
    static int program(String fragment) {
        int vertex = 0, pixel = 0, program = 0;
        try {
            vertex = shader(GL_VERTEX_SHADER, "scene_color/fullscreen.vert"); pixel = shader(GL_FRAGMENT_SHADER, fragment);
            program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, pixel); glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException failure) { if (program != 0) glDeleteProgram(program); throw failure; }
        finally { if (vertex != 0) glDeleteShader(vertex); if (pixel != 0) glDeleteShader(pixel); }
    }

    /** Restore actual GL state so both Arc3D and Blaze3D keep their cached assumptions. */
    private static final class GlState implements AutoCloseable {
        final int framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        final int[] textures = new int[2], samplers = new int[2];
        final int[] viewport = new int[4], scissor = new int[4];
        final boolean depth = glIsEnabled(GL_DEPTH_TEST), stencil = glIsEnabled(GL_STENCIL_TEST);
        final boolean cull = glIsEnabled(GL_CULL_FACE), blend = glIsEnabled(GL_BLEND);
        final boolean srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB), scissorEnabled = glIsEnabled(GL_SCISSOR_TEST);
        final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        final boolean[] colorMask = new boolean[4];
        GlState() {
            glGetIntegerv(GL_VIEWPORT, viewport); glGetIntegerv(GL_SCISSOR_BOX, scissor);
            for (int unit = 0; unit < 2; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit); textures[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
                samplers[unit] = glGetIntegeri(GL_SAMPLER_BINDING, unit);
            }
            try (var stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4); glGetBooleanv(GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
            }
        }
        @Override public void close() {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glUseProgram(program); glBindVertexArray(vao);
            for (int unit = 0; unit < 2; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, textures[unit]); glBindSampler(unit, samplers[unit]);
            }
            glActiveTexture(activeTexture);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            enable(GL_DEPTH_TEST, depth); enable(GL_STENCIL_TEST, stencil); enable(GL_CULL_FACE, cull);
            enable(GL_BLEND, blend); enable(GL_FRAMEBUFFER_SRGB, srgb); enable(GL_SCISSOR_TEST, scissorEnabled);
            glDepthMask(depthMask); glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        }
        private static void enable(int capability, boolean enabled) { if (enabled) glEnable(capability); else glDisable(capability); }
    }
}
