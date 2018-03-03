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

%.so:
	$(CC) $(CFLAGS) -o $@ $^

%.jnilib: %.so
	cp $^ $@

.PHONY: all clean rebuild
