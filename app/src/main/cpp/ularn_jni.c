/* JNI bindings for com.ularn.android.NativeBridge */
#include <jni.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "spawn.h"

static char **to_cstr_array(JNIEnv *env, jobjectArray arr, int *count)
{
    int n = arr ? (*env)->GetArrayLength(env, arr) : 0, i;
    char **out = calloc((size_t)n + 1, sizeof(char *));
    for (i = 0; i < n; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        out[i] = strdup(c);           /* must outlive fork (putenv/argv) */
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }
    if (count) *count = n;
    return out;
}

static void free_cstr_array(char **a)
{
    char **p;
    if (!a) return;
    for (p = a; *p; p++) free(*p);
    free(a);
}

JNIEXPORT jintArray JNICALL
Java_com_ularn_android_NativeBridge_spawn(JNIEnv *env, jclass cls,
        jstring jworkdir, jobjectArray jargs, jobjectArray jenv,
        jint rows, jint cols)
{
    const char *wd = (*env)->GetStringUTFChars(env, jworkdir, NULL);
    char *workdir = strdup(wd);
    int argc = 0, fd;
    char **argv, **envp;
    pid_t pid = -1;
    jint result[2];
    jintArray out;
    (void)cls;

    (*env)->ReleaseStringUTFChars(env, jworkdir, wd);
    argv = to_cstr_array(env, jargs, &argc);
    envp = to_cstr_array(env, jenv, NULL);

    fd = ularn_spawn(workdir, argc, argv, envp, rows, cols, &pid);

    /* Parent copies are no longer needed (the child has its own). */
    free(workdir);
    free_cstr_array(argv);
    free_cstr_array(envp);

    result[0] = fd;
    result[1] = (jint)pid;
    out = (*env)->NewIntArray(env, 2);
    (*env)->SetIntArrayRegion(env, out, 0, 2, result);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_ularn_android_NativeBridge_waitFor(JNIEnv *env, jclass cls, jint pid)
{
    (void)env; (void)cls;
    return ularn_wait((pid_t)pid);
}

JNIEXPORT void JNICALL
Java_com_ularn_android_NativeBridge_setSize(JNIEnv *env, jclass cls,
        jint fd, jint rows, jint cols)
{
    (void)env; (void)cls;
    ularn_set_size(fd, rows, cols);
}

JNIEXPORT void JNICALL
Java_com_ularn_android_NativeBridge_sendSignal(JNIEnv *env, jclass cls,
        jint pid, jint sig)
{
    (void)env; (void)cls;
    if (pid > 0) kill((pid_t)pid, sig);
}
