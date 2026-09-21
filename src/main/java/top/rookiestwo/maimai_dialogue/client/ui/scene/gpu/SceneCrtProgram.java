package top.rookiestwo.maimai_dialogue.client.ui.scene.gpu;

import icyllis.arc3d.opengl.GLTexture;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.presentation.filter.CrtFilter;
import static org.lwjgl.opengl.GL33C.*;

/** CRT sampling and optional quarter-resolution bloom, sharing the scene renderer's GL state guard and FBO. */
final class SceneCrtProgram implements AutoCloseable {
    private int crt, extract, blur, sampler;
    private int geometryLocation, effectsLocation, sizeLocation, timeLocation, directionLocation, featherLocation;

    void prepare(boolean bloom) {
        if (crt == 0) {
            crt = SceneColorRenderer.program("scene_crt/crt.frag");
            geometryLocation = glGetUniformLocation(crt, "Geometry"); effectsLocation = glGetUniformLocation(crt, "Effects");
            sizeLocation = glGetUniformLocation(crt, "SceneSize"); timeLocation = glGetUniformLocation(crt, "Time");
            featherLocation = glGetUniformLocation(crt, "EdgeFeather");
            glUseProgram(crt); glUniform1i(glGetUniformLocation(crt, "Scene"), 0); glUniform1i(glGetUniformLocation(crt, "Bloom"), 1);
            sampler = glGenSamplers();
            glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            MaiMaiDialogue.LOGGER.info("Scene GPU CRT: shader compile/link OK");
        }
        if (bloom && extract == 0) {
            extract = SceneColorRenderer.program("scene_crt/bloom_extract.frag");
            try { blur = SceneColorRenderer.program("scene_crt/bloom_blur.frag"); }
            catch (RuntimeException failure) { glDeleteProgram(extract); extract = 0; throw failure; }
            glUseProgram(extract); glUniform1i(glGetUniformLocation(extract, "Scene"), 0);
            glUseProgram(blur); glUniform1i(glGetUniformLocation(blur, "Scene"), 0);
            directionLocation = glGetUniformLocation(blur, "Direction");
            MaiMaiDialogue.LOGGER.info("Scene GPU CRT: bloom extraction and blur shaders compile/link OK");
        }
    }

    void draw(GLTexture source, GLTexture target, GLTexture bloomA, GLTexture bloomB, CrtFilter settings, float time) {
        glActiveTexture(GL_TEXTURE0); glBindSampler(0, sampler);
        if (settings.bloom() > 0) {
            if (bloomA == null || bloomB == null) throw new IllegalStateException("Missing CRT bloom surfaces");
            SceneColorRenderer.bindTarget(bloomA); glUseProgram(extract); texture(source); draw();
            SceneColorRenderer.bindTarget(bloomB); glUseProgram(blur); texture(bloomA);
            glUniform2f(directionLocation, 1f / bloomA.getWidth(), 0); draw();
            SceneColorRenderer.bindTarget(bloomA); texture(bloomB);
            glUniform2f(directionLocation, 0, 1f / bloomB.getHeight()); draw();
        }
        SceneColorRenderer.bindTarget(target); glUseProgram(crt); texture(source);
        glActiveTexture(GL_TEXTURE1); glBindSampler(1, sampler);
        glBindTexture(GL_TEXTURE_2D, bloomA == null ? source.getHandle() : bloomA.getHandle());
        glUniform4f(geometryLocation, settings.curvature(), settings.scanlineStrength(), settings.maskStrength(), settings.chromaticAberration());
        glUniform4f(effectsLocation, settings.vignette(), settings.noise(), settings.flicker(), settings.bloom());
        glUniform2f(sizeLocation, source.getWidth(), source.getHeight()); glUniform1f(timeLocation, time);
        glUniform1f(featherLocation, settings.edgeFeather());
        draw();
    }

    private static void texture(GLTexture texture) { glBindTexture(GL_TEXTURE_2D, texture.getHandle()); }
    private static void draw() { glDrawArrays(GL_TRIANGLES, 0, 3); }

    @Override public void close() {
        if (crt != 0) glDeleteProgram(crt);
        if (extract != 0) glDeleteProgram(extract);
        if (blur != 0) glDeleteProgram(blur);
        if (sampler != 0) glDeleteSamplers(sampler);
        crt = extract = blur = sampler = 0;
    }
}
