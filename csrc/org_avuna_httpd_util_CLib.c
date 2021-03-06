#include <jni.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <string.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <poll.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    socket
 * Signature: (III)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_socket(JNIEnv * this, jclass cls, jint domain, jint type, jint protocol) {
	int i = socket(domain, type, protocol);
	if(i >= 0) {
		int on = 1;
		int e = errno;
		setsockopt(i, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on)); // we don't care if it fails.
		setsockopt(i, IPPROTO_TCP, TCP_NODELAY, &on, sizeof(on));
		errno = e;// if the above does fail, we don't want to tell Java about it.
	}
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    bindUnix
 * Signature: (ILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_bindUnix(JNIEnv * this, jclass cls, jint sockfd, jstring path) {
	struct sockaddr_un sun;
	sun.sun_family = 1;
	const char *npath = (*this)->GetStringUTFChars(this, path, 0);
	strcpy(sun.sun_path, npath);
	int i = bind(sockfd, (struct sockaddr *)&sun, sizeof(sun));
	(*this)->ReleaseStringUTFChars(this, path, 0);
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    bindTCP
 * Signature: (ILjava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_bindTCP(JNIEnv * this, jclass cls, jint sockfd, jstring ip, jint port) {
	struct sockaddr_in sin;
	memset(&sin, 0, sizeof(sin));
	sin.sin_family = AF_INET;
	const char *nip = (*this)->GetStringUTFChars(this, ip, 0);
	if(inet_pton(AF_INET, nip, (struct in_addr *)&sin.sin_addr) < 1) {
		return -1;
	}
	sin.sin_port = htons(port);
	int i = bind(sockfd, (struct sockaddr *)&sin, sizeof(sin));
	(*this)->ReleaseStringUTFChars(this, ip, 0);
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    listen
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_listen(JNIEnv * this, jclass cls, jint sockfd, jint backlog) {
	return listen(sockfd, backlog);
}

char* itoa(int val) {
	char *ret = malloc(32);
	sprintf(ret, "%d", val);
	return ret;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    acceptUnix
 * Signature: (ILjava/lang/String;I)I
 */
JNIEXPORT jstring JNICALL Java_org_avuna_httpd_util_CLib_acceptUnix(JNIEnv * this, jclass cls, jint sockfd) {
	struct sockaddr_un sun;
	sun.sun_family = AF_INET;
	char *fpath = malloc(32);
	if(fpath == NULL) {
		return NULL;
	}
	socklen_t slt = sizeof(sun);
	int i = accept(sockfd, (struct sockaddr *)&sun, &slt);
	if(i < 0) {
		fpath = malloc(8);
		if(fpath == NULL) {
			return NULL;
		}
		strcpy(fpath, "-1/null");
		//*fpath = "-1/null";
	} else {
		int on = 1;
		setsockopt(i, IPPROTO_TCP, TCP_NODELAY, &on, sizeof(on));
		free(fpath);
		fpath = itoa(i);
		char *cr1;
		cr1 = malloc(strlen(fpath) + 1 + strlen((char *)&sun.sun_path) + 1);
		if(cr1 == NULL) {
			return NULL;
		}
		strcpy(cr1, fpath);
		strcat(cr1, "/");
		strcat(cr1, (char *)&sun.sun_path);
		free(fpath);
		fpath = cr1;
	}
	jstring js = (*this)->NewStringUTF(this, fpath);
	free(fpath);
	return js;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    acceptUnix
 * Signature: (ILjava/lang/String;I)I
 */
JNIEXPORT jstring JNICALL Java_org_avuna_httpd_util_CLib_acceptTCP(JNIEnv * this, jclass cls, jint sockfd) {
	struct sockaddr_in sin;
	sin.sin_family = AF_INET;
	socklen_t slt = sizeof(sin);
	char* ret;
	int m = 0;
	int i = accept(sockfd, (struct sockaddr *)&sin, &slt);
	if(i < 0) {
		ret = "-1/null";
	} else {
		int on = 1;
		setsockopt(i, IPPROTO_TCP, TCP_NODELAY, &on, sizeof(on));
		int f;
		if ((f = fcntl(sockfd, F_GETFL, 0)) == -1) f = 0;
		fcntl(i, F_SETFL, f | O_NONBLOCK); // if this fails, it's only a precaution, unnecessary.
		char* dp = itoa(i);
		char* ip = inet_ntoa(sin.sin_addr);
		ret = malloc(strlen(dp) + 1 + strlen(ip) + 1);
		if(ret == NULL) {
			return NULL;
		}
		strcpy(ret, dp);
		free(dp);
		strcat(ret, "/");
		strcat(ret, ip);
		m = 1;
	}
	jstring js = (*this)->NewStringUTF(this, ret);
	if(m == 1)free(ret);
	return js;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    read
 * Signature: (I[BI)I
 */
JNIEXPORT jbyteArray JNICALL Java_org_avuna_httpd_util_CLib_read(JNIEnv * this, jclass cls, jint sockfd, jint size) {
	jbyte* ra = malloc(size);
	memset(ra, 0, size);
	int i = read(sockfd, ra, size);
	if(i < 0) {
		i = 0;
	}
	jbyteArray f = (*this)->NewByteArray(this, i);
	if (f == NULL) {
		return NULL;
	}
	if(i >= 0) {
		(*this)->SetByteArrayRegion(this, f, 0, i, (jbyte*)ra);
	}
	free(ra);
	return f;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    write
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_write(JNIEnv * this, jclass cls, jint sockfd, jbyteArray buf) {
	jbyte* jb = (*this)->GetByteArrayElements(this, buf, 0);
	int i = write(sockfd, jb, (*this)->GetArrayLength(this, buf));
	(*this)->ReleaseByteArrayElements(this, buf, jb, 0);
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    connect
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_connect(JNIEnv * this, jclass cls, jint sockfd, jstring path) {
	const char *npath = (*this)->GetStringUTFChars(this, path, 0);
	struct sockaddr_un sun;
	sun.sun_family = AF_UNIX;
	strncpy(sun.sun_path, npath, 108);
	int i = connect((int)sockfd, (struct sockaddr *)&sun, sizeof(sun));
	(*this)->ReleaseStringUTFChars(this, path, 0);
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    close
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_close(JNIEnv * this, jclass cls, jint sockfd) {
	return close(sockfd);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    umask
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_umask(JNIEnv * this, jclass cls, jint um) {
	return umask(um);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    setuid
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_setuid(JNIEnv * this, jclass cls, jint uid) {
	return setuid(uid);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    setgid
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_setgid(JNIEnv * this, jclass cls, jint gid) {
	return setgid(gid);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    getuid
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_getuid(JNIEnv * this, jclass cls) {
	return getuid();
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    getgid
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_getgid(JNIEnv * this, jclass cls) {
	return getgid();
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    seteuid
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_seteuid(JNIEnv * this, jclass cls, jint euid) {
	return seteuid(euid);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    geteuid
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_geteuid(JNIEnv * this, jclass cls) {
	return geteuid();
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    setegid
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_setegid(JNIEnv * this, jclass cls, jint egid) {
	return setegid(egid);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    getegid
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_getegid(JNIEnv * this, jclass cls) {
	return getegid();
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    fflush
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_fflush(JNIEnv * this, jclass env, jint sockfd) {
	return fflush((FILE *)&sockfd);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    __xstat64
 * Signature: (ILjava/lang/String;[B)I
 */
JNIEXPORT jstring JNICALL Java_org_avuna_httpd_util_CLib_stat(JNIEnv * this, jclass cls, jstring path) {
	struct stat s;
	char ret[128]; //more than we could possibly ever need
	const char *npath = (*this)->GetStringUTFChars(this, path, 0);
	int i = stat(npath, &s);
	(*this)->ReleaseStringUTFChars(this, path, 0);
	if(i == -1) {
		char *tcpy = "-1";
		strcpy(ret, tcpy);
	} else {
		char *sep = "/";
		char *tmp = itoa(s.st_nlink);
		strcpy(ret, tmp);
		free(tmp);
		strcat(ret, sep);
		tmp = itoa(s.st_uid);
		strcat(ret, itoa(s.st_uid));
		free(tmp);
		strcat(ret, sep);
		tmp = itoa(s.st_gid);
		strcat(ret, itoa(s.st_gid));
		free(tmp);
		strcat(ret, sep);
		tmp = itoa(s.st_mode);
		strcat(ret, itoa(s.st_mode));
		free(tmp);
	}
	return (*this)->NewStringUTF(this, ret);
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    readlink
 * Signature: (Ljava/lang/String;[BI)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_readlink(JNIEnv * this, jclass cls, jstring path, jbyteArray buf) {
	jbyte* jb = (*this)->GetByteArrayElements(this, buf, 0);
	const char *npath = (*this)->GetStringUTFChars(this, path, 0);
	int i = readlink(npath, jb, (*this)->GetArrayLength(this, buf));
	(*this)->ReleaseStringUTFChars(this, path, 0);
	(*this)->ReleaseByteArrayElements(this, buf, jb, 0);
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    chmod
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_chmod(JNIEnv * this, jclass cls, jstring path, jint ch) {
	const char *npath = (*this)->GetStringUTFChars(this, path, 0);
	int i = chmod(npath, ch);
	(*this)->ReleaseStringUTFChars(this, path, 0);
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    lchown
 * Signature: (Ljava/lang/String;II)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_lchown(JNIEnv * this, jclass cls, jstring path, jint uid, jint gid) {
	const char *npath = (*this)->GetStringUTFChars(this, path, 0);
	int i = lchown(npath, uid, gid);
	(*this)->ReleaseStringUTFChars(this, path, 0);
	return i;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    available
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_available(JNIEnv * this, jclass cls, jint sockfd) {
	jint z = 0;
	int i = ioctl(sockfd, FIONREAD, &z);
	if(i < 0) {
		return -1;
	}
	return z;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    errno
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_errno(JNIEnv * this, jclass cls) {
	return errno;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    poll
 * Signature: ([I)[I
 */
JNIEXPORT jintArray JNICALL Java_org_avuna_httpd_util_CLib_poll(JNIEnv * this, jclass cls, jintArray sockfds) {
	jsize size = (*this)->GetArrayLength(this, sockfds);
	jint *body = (*this)->GetIntArrayElements(this, sockfds, 0);
	struct pollfd fds[size];
	for(int i = 0;i<size;i++) {
		struct pollfd fd;
		fd.fd = body[i];
		fd.events = POLLIN;
		fd.revents = 0;
		fds[i] = fd;
	}
	if(poll(&fds[0], (nfds_t)size, 10000) < 0) return NULL;
	jintArray pr;
	pr = (*this)->NewIntArray(this, size);
	if(pr == NULL) return NULL;
	jint prr[size];
	for(int i = 0;i<size;i++) {
		prr[i] = fds[i].revents;
	}
	(*this)->SetIntArrayRegion(this, pr, 0, size, &prr[0]);
	return pr;
}

/*
 * Class:     org_avuna_httpd_util_CLib
 * Method:    hasGNUTLS
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_avuna_httpd_util_CLib_hasGNUTLS(JNIEnv * this, jclass cls) {
#ifdef BIT64
	return 1;
#else
	return 0;
#endif
}
