#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "runtime.h"
typedef struct { JNIEnv *env; jobject host; jmethodID call; } Host;
static jbyteArray bytes(JNIEnv *env, const char *text) {
  size_t n=strlen(text); jbyteArray value=(*env)->NewByteArray(env,(jsize)n);
  if(value) (*env)->SetByteArrayRegion(env,value,0,(jsize)n,(const jbyte *)text);
  return value;
}
static char *call_host(void *opaque, const char *request) {
  Host *h=opaque; JNIEnv *env=h->env;
  jbyteArray input=bytes(env,request);
  jbyteArray result=(*env)->CallObjectMethod(env,h->host,h->call,input);
  (*env)->DeleteLocalRef(env,input);
  if((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return NULL; }
  if(!result) return NULL;
  jsize n=(*env)->GetArrayLength(env,result); char *copy=calloc((size_t)n+1,1);
  if(copy) (*env)->GetByteArrayRegion(env,result,0,n,(jbyte *)copy);
  (*env)->DeleteLocalRef(env,result); return copy;
}
JNIEXPORT jbyteArray JNICALL Java_io_github_toanhp123_hikari_extensions_lnreader_BoundedQuickJs_executeNative(
 JNIEnv *env,jobject self,jbyteArray script,jobject host) {
  (void)self;
  jsize n=(*env)->GetArrayLength(env,script);
  if(n>4*1024*1024) return NULL;
  char *text=calloc((size_t)n+1,1); if(!text) return NULL;
  (*env)->GetByteArrayRegion(env,script,0,n,(jbyte *)text);
  if(memchr(text,0,(size_t)n)) { free(text); return NULL; }
  jclass type=(*env)->GetObjectClass(env,host);
  Host h={env,host,(*env)->GetMethodID(env,type,"callBytes","([B)[B")};
  (*env)->DeleteLocalRef(env,type);
  char *result=h.call?hikari_js(text,5000,call_host,&h):NULL; free(text);
  if(!result) return NULL;
  jbyteArray output=bytes(env,result); free(result); return output;
}
