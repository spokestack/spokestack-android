LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := spokestack
LOCAL_SRC_FILES := \
	vad.cpp \
	libfvad/src/signal_processing/division_operations.c \
	libfvad/src/signal_processing/energy.c \
	libfvad/src/signal_processing/get_scaling_square.c \
	libfvad/src/signal_processing/resample_48khz.c \
	libfvad/src/signal_processing/resample_by_2_internal.c \
	libfvad/src/signal_processing/resample_fractional.c \
	libfvad/src/signal_processing/spl_inl.c \
	libfvad/src/vad/vad_core.c \
	libfvad/src/vad/vad_filterbank.c \
	libfvad/src/vad/vad_gmm.c \
	libfvad/src/vad/vad_sp.c \
	libfvad/src/fvad.c

include $(BUILD_SHARED_LIBRARY)

# build the library on the host platform
$(info $(shell cd jni && make -f Dev.mk))
