#include <malloc.h>
#include "librtmp.h"
#include "librtmp/rtmp.h"
#include <android/log.h>
#include <string.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "RTMP", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "RTMP", __VA_ARGS__)

 JNIEXPORT jstring JNICALL Java_com_hezb_lib_rtmp_RtmpClient_getVersion
 (JNIEnv * env, jclass clazz) {
    RTMP_LibVersion();
    char ver[8];
    sprintf(ver, "%x", RTMP_LibVersion());
    return (*env)->NewStringUTF(env, ver);
 }

 JNIEXPORT jlong JNICALL Java_com_hezb_lib_rtmp_RtmpClient_open
 (JNIEnv * env, jobject thiz, jstring url_, jboolean isPublishMode) {
 	const char *url = (*env)->GetStringUTFChars(env, url_, 0);
 	LOGD("RTMP_OPENING : %s", url);
 	RTMP* rtmp = RTMP_Alloc();
 	if (rtmp == NULL) {
 		LOGE("RTMP_Alloc=NULL");
 		return 0;
 	}

 	RTMP_Init(rtmp);
 	int ret = RTMP_SetupURL(rtmp, url);

 	if (!ret) {
 		RTMP_Free(rtmp);
 		rtmp=NULL;
 		LOGE("RTMP_SetupURL=%d", ret);
 		return 0;
 	}
 	if (isPublishMode) {
 		RTMP_EnableWrite(rtmp);
 	}

 	ret = RTMP_Connect(rtmp, NULL);
 	if (!ret) {
 		RTMP_Free(rtmp);
 		rtmp=NULL;
 		LOGD("RTMP_Connect=%d", ret);
 		return 0;
 	}
 	ret = RTMP_ConnectStream(rtmp, 0);

 	if (!ret) {
 		RTMP_Close(rtmp);
 		RTMP_Free(rtmp);
 		rtmp=NULL;
 		LOGD("RTMP_ConnectStream=%d", ret);
 		return 0;
 	}
 	(*env)->ReleaseStringUTFChars(env, url_, url);
 	LOGD("RTMP_OPENED");
 	return (jlong) rtmp;
 }

 JNIEXPORT jint JNICALL Java_com_hezb_lib_rtmp_RtmpClient_read
 (JNIEnv * env, jobject thiz, jlong rtmp, jbyteArray data_, jint offset, jint size) {

 	char* data = malloc(size*sizeof(char));

 	int readCount = RTMP_Read((RTMP*)rtmp, data, size);

 	if (readCount > 0) {
        (*env)->SetByteArrayRegion(env, data_, offset, readCount, data);  // copy
    }
    free(data);

    return readCount;
}

 JNIEXPORT jint JNICALL Java_com_hezb_lib_rtmp_RtmpClient_write
 (JNIEnv * env, jobject thiz, jlong rtmp, jbyteArray data, jint size, jint type, jint ts) {
 	// LOGD("start write");
 	jbyte *buffer = (*env)->GetByteArrayElements(env, data, NULL);
 	RTMPPacket *packet = (RTMPPacket*)malloc(sizeof(RTMPPacket));
 	RTMPPacket_Alloc(packet, size);
 	RTMPPacket_Reset(packet);
    if (type == RTMP_PACKET_TYPE_INFO) { // metadata
    	packet->m_nChannel = 0x03;
    } else if (type == RTMP_PACKET_TYPE_VIDEO) { // video
    	packet->m_nChannel = 0x04;
    } else if (type == RTMP_PACKET_TYPE_AUDIO) { //audio
    	packet->m_nChannel = 0x05;
    } else {
    	packet->m_nChannel = -1;
    }

    packet->m_nInfoField2  =  ((RTMP*)rtmp)->m_stream_id;

    // LOGD("write data type: %d, ts %d", type, ts);

    memcpy(packet->m_body,  buffer,  size);
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_hasAbsTimestamp = FALSE;
    packet->m_nTimeStamp = ts;
    packet->m_packetType = type;
    packet->m_nBodySize  = size;
    int ret = RTMP_SendPacket((RTMP*)rtmp, packet, 0);
    RTMPPacket_Free(packet);
    free(packet);
    (*env)->ReleaseByteArrayElements(env, data, buffer, 0);
    // if (!ret) {
    // 	LOGD("end write error %d", ret);
    // }else
    // {
    // 	LOGD("end write success");
    // }

    return ret;
}

 JNIEXPORT void JNICALL Java_com_hezb_lib_rtmp_RtmpClient_close
 (JNIEnv * env, jobject thiz, jlong rtmp) {
 	RTMP_Close((RTMP*)rtmp);
 	RTMP_Free((RTMP*)rtmp);
 }
