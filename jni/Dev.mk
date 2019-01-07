CC     = gcc
CFLAGS = -shared -Wall -O3 -fpic \
         -I$(JAVA_HOME)/include \
         -I$(JAVA_HOME)/include/$(shell uname | tr A-Z a-z)
OUTDIR = ../target

all: $(OUTDIR)/libspokestack.jnilib

clean:
	$(RM) $(OUTDIR)/libspokestack.jnilib
	$(RM) $(OUTDIR)/libspokestack.so

rebuild: clean all

$(OUTDIR)/libspokestack.so: \
	agc.cpp \
	vad.cpp \
	filter_audio/other/complex_bit_reverse.c \
	filter_audio/other/complex_fft.c \
	filter_audio/other/copy_set_operations.c \
	filter_audio/other/cross_correlation.c \
	filter_audio/other/division_operations.c \
	filter_audio/other/dot_product_with_scale.c \
	filter_audio/other/downsample_fast.c \
	filter_audio/other/energy.c \
	filter_audio/other/get_scaling_square.c \
	filter_audio/other/min_max_operations.c \
	filter_audio/other/real_fft.c \
	filter_audio/other/resample_by_2.c \
	filter_audio/other/resample_by_2_internal.c \
	filter_audio/other/resample_fractional.c \
	filter_audio/other/resample_48khz.c \
	filter_audio/other/spl_init.c \
	filter_audio/other/spl_sqrt.c \
	filter_audio/other/vector_scaling_operations.c \
	filter_audio/vad/vad_core.c \
	filter_audio/vad/vad_filterbank.c \
	filter_audio/vad/vad_gmm.c \
	filter_audio/vad/vad_sp.c \
	filter_audio/vad/webrtc_vad.c \
	filter_audio/agc/analog_agc.c \
	filter_audio/agc/digital_agc.c

%.so:
	$(CC) $(CFLAGS) -o $@ $^

%.jnilib: %.so
	cp $^ $@

.PHONY: all clean rebuild
