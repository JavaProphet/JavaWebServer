// provided by IDE or manually defined.
#ifdef BIT64
/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_avuna_httpd_util_CLib */

#ifndef _Included_org_avuna_httpd_util_unio_GNUTLS
#define _Included_org_avuna_httpd_util_unio_GNUTLS
#ifdef __cplusplus
extern "C" {
#endif

	JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_unio_GNUTLS_globalinit(JNIEnv *, jclass);

	JNIEXPORT jlong JNICALL Java_org_avuna_httpd_util_unio_GNUTLS_loadcert(JNIEnv *, jclass, jstring, jstring, jstring);

	JNIEXPORT jlong JNICALL Java_org_avuna_httpd_util_unio_GNUTLS_preaccept(JNIEnv *, jclass, jlong);

	JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_unio_GNUTLS_postaccept(JNIEnv *, jclass, jlong, jint);

	JNIEXPORT jbyteArray JNICALL Java_org_avuna_httpd_util_unio_GNUTLS_read(JNIEnv *, jclass, jlong, jint);

	JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_unio_GNUTLS_write(JNIEnv *, jclass, jlong, jbyteArray);

	JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_unio_GNUTLS_close(JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
#endif