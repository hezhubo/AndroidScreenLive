#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

 JNIEXPORT jstring JNICALL Java_com_hezb_lib_rtmp_RtmpClient_getVersion
 (JNIEnv * env, jclass clazz);

 JNIEXPORT jlong JNICALL Java_com_hezb_lib_rtmp_RtmpClient_open
 (JNIEnv * env, jobject thiz, jstring url_, jboolean isPublishMode);

 JNIEXPORT jint JNICALL Java_com_hezb_lib_rtmp_RtmpClient_read
 (JNIEnv * env, jobject thiz, jlong rtmp, jbyteArray data_, jint offset, jint size);

 JNIEXPORT jint JNICALL Java_com_hezb_lib_rtmp_RtmpClient_write
 (JNIEnv * env, jobject thiz, jlong rtmp, jbyteArray data, jint size, jint type, jint ts);

 JNIEXPORT void JNICALL Java_com_hezb_lib_rtmp_RtmpClient_close
 (JNIEnv * env, jobject thiz, jlong rtmp);


#ifdef __cplusplus
}
#endif
