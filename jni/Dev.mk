CC           = gcc
CFLAGS       = -dynamiclib -Wall -O3 -fpic \
					-I$(shell /usr/libexec/java_home)/include \
					-I$(shell /usr/libexec/java_home)/include/$(shell uname)
OUTDIR=../target

all: $(OUTDIR)/libspokestack.jnilib

clean:
	$(RM) $(OUTDIR)/libspokestack.jnilib

rebuild: clean all

$(OUTDIR)/libspokestack.jnilib: \
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

%.jnilib:
	$(CC) $(CFLAGS) -o $@ $^

.PHONY: all clean rebuild
