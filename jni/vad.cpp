#include <jni.h>

extern "C" {
#  include "libfvad/include/fvad.h"
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_pylon_spokestack_libfvad_VADTrigger_create(
      JNIEnv* env,
      jobject self,
      jint    mode,
      jint    rate) {
   Fvad* vad = fvad_new();
   fvad_set_mode(vad, mode);
   fvad_set_sample_rate(vad, rate);
   return (jlong)vad;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pylon_spokestack_libfvad_VADTrigger_destroy(
      JNIEnv* env,
      jobject self,
      jlong   vad) {
   fvad_free((Fvad*)vad);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pylon_spokestack_libfvad_VADTrigger_process(
      JNIEnv* env,
      jobject self,
      jlong   vad,
      jobject buffer,
      jint    length) {
   int16_t* frame = (int16_t*)env->GetDirectBufferAddress(buffer);
   return fvad_process((Fvad*)vad, frame, length / 2);
}
