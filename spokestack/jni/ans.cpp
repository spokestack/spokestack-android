/****************************************************************************
 *
 * MODULE:  ans.cpp
 * PURPOSE: webrtc acoustic noise suppression (ans) jni wrapper
 *
 ***************************************************************************/
/*-------------------[       Pre Include Defines       ]-------------------*/
/*-------------------[      Library Include Files      ]-------------------*/
#include <jni.h>
/*-------------------[      Project Include Files      ]-------------------*/
#include "filter_audio/ns/include/noise_suppression_x.h"
/*-------------------[      Macros/Constants/Types     ]-------------------*/
/*-------------------[        Global Variables         ]-------------------*/
/*-------------------[        Global Prototypes        ]-------------------*/
/*-------------------[        Module Variables         ]-------------------*/
/*-------------------[        Module Prototypes        ]-------------------*/
/*-------------------[         Implementation          ]-------------------*/
/*-----------< FUNCTION: AcousticNoiseSuppressor_create >--------------------
// Purpose:    creates and configures a new webrtc noise suppressor component
// Parameters: env  - java environment
//             self - java this reference
//             policy - suppressor policy (0..2) in order of aggressiveness
// Returns:    pointer to the opaque suppressor instance if successful
//             null otherwise
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
jlong JNICALL Java_io_spokestack_spokestack_webrtc_AcousticNoiseSuppressor_create(
      JNIEnv* env,
      jobject self,
      jint    sampleRate,
      jint    policy) {
   // create and initialize the suppressor instance
   NsxHandle* ans = NULL;
   int result = WebRtcNsx_Create(&ans);
   if (result == 0) {
      result = WebRtcNsx_Init(ans, sampleRate);
      // configure the ans instance
      if (result == 0)
         result = WebRtcNsx_set_policy(ans, policy);
      // if something went wrong, cleanup
      if (result != 0) {
         WebRtcNsx_Free(ans);
         ans = NULL;
      }
   }
   return (jlong)ans;
}
/*-----------< FUNCTION: AcousticNoiseSuppressor_destroy >-------------------
// Purpose:    releases ans resources
// Parameters: env  - java environment
//             self - java this reference
//             ans  - ans handle returned by create()
// Returns:    none
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
void JNICALL Java_io_spokestack_spokestack_webrtc_AcousticNoiseSuppressor_destroy(
      JNIEnv* env,
      jobject self,
      jlong   ans) {
   WebRtcNsx_Free((NsxHandle*)ans);
}
/*-----------< FUNCTION: AcousticNoiseSuppressor_process >-------------------
// Purpose:    processes an audio frame, suppressing noise
// Parameters: env    - java environment
//             self   - java this reference
//             ans    - suppressor handle returned by create()
//             buffer - sample buffer (16-bit samples)
//             offset - offset, in bytes, to start reading/writing the buffer
// Returns:    0 if successful
//             -1 on error
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
jint JNICALL Java_io_spokestack_spokestack_webrtc_AcousticNoiseSuppressor_process(
      JNIEnv* env,
      jobject self,
      jlong   ans,
      jobject buffer,
      jint    offset) {
   int16_t* frame = (int16_t*)env->GetDirectBufferAddress(buffer);
   frame += offset / sizeof(int16_t);
   return WebRtcNsx_Process((NsxHandle*)ans, frame, NULL, frame, NULL);
}
