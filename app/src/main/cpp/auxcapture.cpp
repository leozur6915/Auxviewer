#include <jni.h>
#include <GLES2/gl2.h>
#include <string>
#include <thread>
#include <atomic>

static std::atomic<bool> running{false};

extern "C"
JNIEXPORT void JNICALL
Java_com_example_auxviewer_NativeBridge_startCapture(JNIEnv* env, jobject, jstring path) {
    const char* dev = env->GetStringUTFChars(path, nullptr);
    // TODO: open /dev/video0 with V4L2, map buffers, etc.
    running = true;
    env->ReleaseStringUTFChars(path, dev);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_auxviewer_NativeBridge_drawFrame(JNIEnv*, jobject, jboolean mirror, jboolean flip) {
    if (!running) return;
    // TODO: upload frame to GL texture, apply mirror/flip via texture coords
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_auxviewer_NativeBridge_stopCapture(JNIEnv*, jobject) {
    running = false;
    // TODO: close V4L2 device
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_auxviewer_NativeBridge_toggleFormat(JNIEnv*, jobject) {
    // TODO: send ioctl to switch NTSC/PAL on the capture chipset
}
