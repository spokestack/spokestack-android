/****************************************************************************
 *
 * MODULE:  vad.cpp
 * PURPOSE: webrtc voice activity detector (vad) jni wrapper
 *
 ***************************************************************************/
/*-------------------[       Pre Include Defines       ]-------------------*/
/*-------------------[      Library Include Files      ]-------------------*/
#include <jni.h>
/*-------------------[      Project Include Files      ]-------------------*/
#include "filter_audio/vad/include/webrtc_vad.h"
/*-------------------[      Macros/Constants/Types     ]-------------------*/
/*-------------------[        Global Variables         ]-------------------*/
/*-------------------[        Global Prototypes        ]-------------------*/
/*-------------------[        Module Variables         ]-------------------*/
/*-------------------[        Module Prototypes        ]-------------------*/
/*-------------------[         Implementation          ]-------------------*/
/*-----------< FUNCTION: VoiceActivityDetector_create >----------------------
// Purpose:    creates and configures a new webrtc vad component
// Parameters: env  - java environment
//             self - java this reference
//             mode - vad mode (0..3) in order of precision
// Returns:    pointer to the opaque vad instance if successful
//             null otherwise
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
jlong JNICALL Java_io_spokestack_spokestack_webrtc_VoiceActivityDetector_create(
      JNIEnv* env,
      jobject self,
      jint    mode) {
   // create and initialize the vad instance
   VadInst* vad = NULL;
   int result = WebRtcVad_Create(&vad);
   if (result == 0) {
      result = WebRtcVad_Init(vad);
      // configure the vad instance
      if (result == 0)
         result = WebRtcVad_set_mode(vad, mode);
      // if something went wrong, cleanup
      if (result != 0) {
         WebRtcVad_Free(vad);
         vad = NULL;
      }
   }
   return (jlong)vad;
}
/*-----------< FUNCTION: VoiceActivityDetector_destroy >---------------------
// Purpose:    releases vad resources
// Parameters: env  - java environment
//             self - java this reference
//             vad  - vad handle returned by create()
// Returns:    none
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
void JNICALL Java_io_spokestack_spokestack_webrtc_VoiceActivityDetector_destroy(
      JNIEnv* env,
      jobject self,
      jlong   vad) {
   WebRtcVad_Free((VadInst*)vad);
}
/*-----------< FUNCTION: VoiceActivityDetector_process >---------------------
// Purpose:    processes an audio frame, detecting voiced speech
// Parameters: env    - java environment
//             self   - java this reference
//             vad    - vad handle returned by create()
//             rate   - sample rate, in Hz
//             buffer - sample buffer (16-bit samples)
//             length - size, in bytes, of the buffer
// Returns:    1 if voiced speech was detected
//             0 if not detected
//             -1 on error
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
jint JNICALL Java_io_spokestack_spokestack_webrtc_VoiceActivityDetector_process(
      JNIEnv* env,
      jobject self,
      jlong   vad,
      jint    rate,
      jobject buffer,
      jint    length) {
   int16_t* frame = (int16_t*)env->GetDirectBufferAddress(buffer);
   return WebRtcVad_Process((VadInst*)vad, rate, frame, length / 2);
}
