/****************************************************************************
 *
 * MODULE:  agc.cpp
 * PURPOSE: webrtc automatic gain control (agc) jni wrapper
 *
 ***************************************************************************/
/*-------------------[       Pre Include Defines       ]-------------------*/
/*-------------------[      Library Include Files      ]-------------------*/
#include <jni.h>
#include <string.h>
/*-------------------[      Project Include Files      ]-------------------*/
#include "filter_audio/agc/include/gain_control.h"
/*-------------------[      Macros/Constants/Types     ]-------------------*/
/*-------------------[        Global Variables         ]-------------------*/
/*-------------------[        Global Prototypes        ]-------------------*/
/*-------------------[        Module Variables         ]-------------------*/
static int32_t miclevel = 128;
/*-------------------[        Module Prototypes        ]-------------------*/
/*-------------------[         Implementation          ]-------------------*/
/*-----------< FUNCTION: AutomaticGainControl_create >-----------------------
// Purpose:    creates and configures a new webrtc agc component
// Parameters: env               - java environment
//             self              - java this reference
//             rate              - sample rate, in Hz
//             targetLeveldBFS   - target peak energy, in dB full scale
//             compressionGaindB - dynamic range compression rate, in dB
//             limiterEnable     - true to enable the agc's peak limiter
// Returns:    pointer to the opaque agc instance if successful
//             null otherwise
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
jlong JNICALL Java_io_spokestack_spokestack_webrtc_AutomaticGainControl_create(
      JNIEnv*  env,
      jobject  self,
      jint     rate,
      jint     targetLeveldBFS,
      jint     compressionGaindB,
      jboolean limiterEnable) {
   // create and initialize the agc instance
   void* agc = NULL;
   int result = WebRtcAgc_Create(&agc);
   if (result == 0) {
      result = WebRtcAgc_Init(agc, 0, 100, kAgcModeFixedDigital, rate);
      if (result == 0) {
         // configure the agc
         WebRtcAgc_config_t config; memset(&config, 0, sizeof(config));
         config.targetLevelDbfs   = targetLeveldBFS;
         config.limiterEnable     = limiterEnable ? kAgcTrue : kAgcFalse;
         config.compressionGaindB = compressionGaindB;
         result = WebRtcAgc_set_config(agc, config);
      }
      // if something went wrong, clean up
      if (result != 0) {
         WebRtcAgc_Free(agc);
         agc = NULL;
      }
   }
   return (jlong)agc;
}
/*-----------< FUNCTION: AutomaticGainControl_destroy >----------------------
// Purpose:    releases agc resources
// Parameters: env  - java environment
//             self - java this reference
//             agc  - agc handle returned by create()
// Returns:    none
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
void JNICALL Java_io_spokestack_spokestack_webrtc_AutomaticGainControl_destroy(
      JNIEnv* env,
      jobject self,
      jlong   agc) {
   WebRtcAgc_Free((void*)agc);
}
/*-----------< FUNCTION: AutomaticGainControl_process >----------------------
// Purpose:    processes an audio frame, applying gain as needed
// Parameters: env    - java environment
//             self   - java this reference
//             agc    - agc handle returned by create()
//             buffer - sample buffer (16-bit samples)
//             length - size, in bytes, of the buffer
// Returns:    1 if voiced speech was detected
//             0 if not detected
//             -1 on error
---------------------------------------------------------------------------*/
extern "C" JNIEXPORT
jint JNICALL Java_io_spokestack_spokestack_webrtc_AutomaticGainControl_process(
      JNIEnv* env,
      jobject self,
      jlong   agc,
      jobject buffer,
      jint    length) {
   int16_t* frame = (int16_t*)env->GetDirectBufferAddress(buffer);
   uint8_t saturated = 0;
   return WebRtcAgc_Process(
      (void*)agc,
      frame,
      NULL,
      length / 2,
      frame,
      NULL,
      miclevel,
      &miclevel,
      0,
      &saturated);
}
