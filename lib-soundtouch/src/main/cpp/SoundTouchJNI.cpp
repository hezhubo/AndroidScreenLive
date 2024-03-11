#include <jni.h>

#include "include/SoundTouch.h"
#include <android/log.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "SoundTouch", __VA_ARGS__)

using namespace soundtouch;

int sourceChannelCount = 1;

extern "C"
JNIEXPORT jstring JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_getVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(SoundTouch::getVersionString());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_newInstance(JNIEnv *env, jobject thiz) {
    return (jlong)(new SoundTouch());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_deleteInstance(JNIEnv *env, jobject thiz, jlong handle) {
    auto *ptr = (SoundTouch*) handle;
    ptr->clear();
    delete ptr;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setSampleRate(JNIEnv *env, jobject thiz, jlong handle,
                                                      jint sample_rate) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setSampleRate(sample_rate);
    LOGD("setSampleRate : %d", sample_rate);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setChannels(JNIEnv *env, jobject thiz, jlong handle,
                                                    jint channels) {
    sourceChannelCount = channels;
    auto *ptr = (SoundTouch*) handle;
    ptr->setChannels(channels);
    LOGD("setChannels : %d", channels);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setTempo(JNIEnv *env, jobject thiz, jlong handle,
                                                 jfloat tempo) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setTempo(tempo);
    LOGD("setTempo : %f", tempo);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setTempoChange(JNIEnv *env, jobject thiz, jlong handle,
                                                       jint change_tempo) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setTempoChange(change_tempo);
    LOGD("setTempoChange : %d", change_tempo);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setRate(JNIEnv *env, jobject thiz, jlong handle,
                                                jfloat rate) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setRate(rate);
    LOGD("setRate : %f", rate);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setRateChange(JNIEnv *env, jobject thiz, jlong handle,
                                                      jint change_rate) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setRateChange(change_rate);
    LOGD("setRateChange : %d", change_rate);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setPitch(JNIEnv *env, jobject thiz, jlong handle,
                                                 jfloat pitch) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setPitch(pitch);
    LOGD("setPitch : %f", pitch);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setPitchOctaves(JNIEnv *env, jobject thiz, jlong handle,
                                                        jfloat pitch) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setPitchOctaves(pitch);
    LOGD("setPitchOctaves : %f", pitch);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_setPitchSemiTones(JNIEnv *env, jobject thiz, jlong handle,
                                                          jint pitch) {
    auto *ptr = (SoundTouch*) handle;
    ptr->setPitchSemiTones(pitch);
    LOGD("setPitchSemiTones : %d", pitch);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hezb_lib_soundtouch_SoundTouch_process(JNIEnv *env, jobject thiz, jlong handle,
                                                jbyteArray source, jint source_size,
                                                jbyteArray target) {
//    LOGD("--------> start : %d", source_size);
    if (source_size % 2 != 0) {
        return 0;
    }
    jsize length = env->GetArrayLength(source);
    if (length < source_size) {
        return 0;
    }
    jbyte *sourceBytes = env->GetByteArrayElements( source, JNI_FALSE);
    int bufferSize = source_size / 2;
    auto *buffer = new SAMPLETYPE[bufferSize]; // 16位 数组指针 (双字节, 因此size设为source的一半)
    // 读source数据到buffer
    for (int i = 0; i < bufferSize; i++) {
        buffer[i] = (short) ((sourceBytes[i * 2] & 0xFF) | (sourceBytes[i * 2 + 1] << 8));
    }
    env->ReleaseByteArrayElements(source, sourceBytes, 0);

    auto *ptr = (SoundTouch*) handle;
    ptr->putSamples(buffer, bufferSize / sourceChannelCount);

    jsize targetLength = env->GetArrayLength(target);
    jbyte *targetBytes = env->GetByteArrayElements( target, JNI_FALSE);
    uint nSamples;
    int targetIndex = 0;
    // 调用 putSamples 方法后，receiveSamples 并不能马上得到处理后的音频数据，内部是异步执行的音频处理，
    // 因此可能会存在开始是空数据后续又有数据堆积的问题，
    // 考虑到只是做实时音频处理，因此每次只取一次（取消do循环读取）
//    do
//    {
        nSamples = ptr->receiveSamples(buffer, bufferSize / sourceChannelCount);
        if (nSamples > 0) {
            for (int i = 0; i < (nSamples * sourceChannelCount); ++i) {
                if (targetIndex >= targetLength - 1) { // 已经超出了数组大小（其实不必判断，保证source和target的size一致就不会溢出）
                    LOGD("receiveSamples too much! targetLength=%d", targetLength);
//                    nSamples = 0; // 跳出循环
                    break;
                }
                targetBytes[targetIndex] = (jbyte) (buffer[i] & 0xFF);
                targetBytes[targetIndex + 1] = (jbyte) (buffer[i] >> 8);
                targetIndex += 2;
            }
        }
//    } while (nSamples != 0);

    env->ReleaseByteArrayElements(target, targetBytes, 0);
    free(buffer);
//    LOGD("end : %d", targetIndex);
    if (targetIndex > targetLength) {
        return targetLength;
    }
    return targetIndex;
}