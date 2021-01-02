package com.audiogram.videogenerator.utility;


import com.xuggle.ferry.JNIMemoryManager;
import com.xuggle.ferry.JNIMemoryManager.MemoryModel;
import com.xuggle.mediatool.AMediaCoderMixin;
import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.event.*;
import com.xuggle.xuggler.*;
import com.xuggle.xuggler.IAudioSamples.Format;
import com.xuggle.xuggler.ICodec.ID;
import com.xuggle.xuggler.IPixelFormat.Type;
import com.xuggle.xuggler.IStreamCoder.Flags;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MediaWriterMod extends AMediaCoderMixin implements IMediaWriter {
    private static final Type DEFAULT_PIXEL_TYPE;
    private static final Format DEFAULT_SAMPLE_FORMAT;
    private static final IRational DEFAULT_TIMEBASE;

    static {
        JNIMemoryManager.setMemoryModel(MemoryModel.NATIVE_BUFFERS);
        DEFAULT_PIXEL_TYPE = Type.YUV420P;
        DEFAULT_SAMPLE_FORMAT = Format.FMT_S16;
        DEFAULT_TIMEBASE = IRational.make(1, (int) Global.DEFAULT_PTS_PER_SECOND);
    }

    private final Logger log;
    private final IContainer mInputContainer;
    private final Collection<IStream> mOpenedStreams;
    private IContainerFormat mContainerFormat;
    private Map<Integer, Integer> mOutputStreamIndices;
    private Map<Integer, IStream> mStreams;
    private Map<Integer, IConverter> mVideoConverters;
    private boolean mForceInterleave;
    private boolean mMaskLateStreamException;

    MediaWriterMod(String url, IMediaReader reader) {
        this(url, reader.getContainer());
        if (reader.canAddDynamicStreams()) {
            throw new IllegalArgumentException("inputContainer is improperly configured to allow dynamic adding of streams.");
        }
    }

    MediaWriterMod(String url, IContainer inputContainer) {
        super(url, IContainer.make());
        this.log = LoggerFactory.getLogger(this.getClass());
        this.log.trace("<init>");
        this.mOutputStreamIndices = new HashMap();
        this.mStreams = new HashMap();
        this.mVideoConverters = new HashMap();
        this.mOpenedStreams = new Vector();
        this.mForceInterleave = true;
        this.mMaskLateStreamException = false;
        if (inputContainer.getType() != com.xuggle.xuggler.IContainer.Type.READ) {
            throw new IllegalArgumentException("inputContainer is improperly must be of type readable.");
        } else if (inputContainer.canStreamsBeAddedDynamically()) {
            throw new IllegalArgumentException("inputContainer is improperly configured to allow dynamic adding of streams.");
        } else {
            this.mInputContainer = inputContainer;
            this.mContainerFormat = IContainerFormat.make();
            this.mContainerFormat.setOutputFormat(this.mInputContainer.getContainerFormat().getInputFormatShortName(), this.getUrl(), null);
        }
    }

    public MediaWriterMod(String url) {
        super(url, IContainer.make());
        this.log = LoggerFactory.getLogger(this.getClass());
        this.log.trace("<init>");
        this.mOutputStreamIndices = new HashMap();
        this.mStreams = new HashMap();
        this.mVideoConverters = new HashMap();
        this.mOpenedStreams = new Vector();
        this.mForceInterleave = true;
        this.mMaskLateStreamException = false;
        this.mInputContainer = null;
        this.mContainerFormat = null;
    }

    private static String getErrorMessage(int rv) {
        String errorString = "";
        IError error = IError.make(rv);
        if (error != null) {
            errorString = error.toString();
            error.delete();
        }

        return errorString;
    }

    public int addAudioStream(int inputIndex, int streamId, int channelCount, int sampleRate) {
        IContainerFormat format = null;
        if (this.getContainer() != null) {
            format = this.getContainer().getContainerFormat();
        }

        if (format != null && !format.isOutput()) {
            format.delete();
            format = null;
        }

        String url = this.getUrl();
        if (format != null || url != null && url.length() >= 0) {
            ICodec codec = ICodec.guessEncodingCodec(format, null, url, null, com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_AUDIO);
            if (codec == null) {
                throw new UnsupportedOperationException("could not guess audio codec");
            } else {
                int var8;
                try {
                    var8 = this.addAudioStream(inputIndex, streamId, codec, channelCount, sampleRate);
                } finally {
                    if (codec != null) {
                        codec.delete();
                    }

                }

                return var8;
            }
        } else {
            throw new IllegalArgumentException("Cannot guess codec without container or url");
        }
    }

    public int addAudioStream(int inputIndex, int streamId, ID codecId, int channelCount, int sampleRate) {
        if (codecId == null) {
            throw new IllegalArgumentException("null codecId");
        } else {
            ICodec codec = ICodec.findEncodingCodec(codecId);
            if (codec == null) {
                throw new UnsupportedOperationException("cannot encode with codec: " + codecId);
            } else {
                int var7;
                try {
                    var7 = this.addAudioStream(inputIndex, streamId, codec, channelCount, sampleRate);
                } finally {
                    codec.delete();
                }

                return var7;
            }
        }
    }

    public int addAudioStream(int inputIndex, int streamId, ICodec codec, int channelCount, int sampleRate) {
        if (channelCount <= 0) {
            throw new IllegalArgumentException("invalid channel count " + channelCount);
        } else if (sampleRate <= 0) {
            throw new IllegalArgumentException("invalid sample rate " + sampleRate);
        } else {
            IStream stream = this.establishStream(inputIndex, streamId, codec);
            IStreamCoder coder = stream.getStreamCoder();
            coder.setChannels(channelCount);
            coder.setSampleRate(sampleRate);
            coder.setSampleFormat(DEFAULT_SAMPLE_FORMAT);
            this.addStream(stream, inputIndex, stream.getIndex());
            return stream.getIndex();
        }
    }

    public int addVideoStream(int inputIndex, int streamId, int width, int height) {
        return this.addVideoStream(inputIndex, streamId, (IRational) null, width, height);
    }

    public int addVideoStream(int inputIndex, int streamId, IRational frameRate, int width, int height) {
        IContainerFormat format = null;
        if (this.getContainer() != null) {
            format = this.getContainer().getContainerFormat();
        }

        if (format != null && !format.isOutput()) {
            format.delete();
            format = null;
        }

        String url = this.getUrl();
        if (format != null || url != null && url.length() >= 0) {
            ICodec codec = ICodec.guessEncodingCodec(format, null, url, null, com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_VIDEO);
            if (codec == null) {
                throw new UnsupportedOperationException("could not guess video codec");
            } else {
                int var9;
                try {
                    var9 = this.addVideoStream(inputIndex, streamId, codec, frameRate, width, height);
                } finally {
                    if (codec != null) {
                        codec.delete();
                    }

                }

                return var9;
            }
        } else {
            throw new IllegalArgumentException("Cannot guess codec without container or url");
        }
    }

    public int addVideoStream(int inputIndex, int streamId, ID codecId, int width, int height) {
        return this.addVideoStream(inputIndex, streamId, codecId, null, width, height);
    }

    public int addVideoStream(int inputIndex, int streamId, ID codecId, IRational frameRate, int width, int height) {
        if (codecId == null) {
            throw new IllegalArgumentException("null codecId");
        } else {
            ICodec codec = ICodec.findEncodingCodec(codecId);
            if (codec == null) {
                throw new UnsupportedOperationException("cannot encode with codec: " + codecId);
            } else {
                int var8;
                try {
                    var8 = this.addVideoStream(inputIndex, streamId, codec, frameRate, width, height);
                } finally {
                    codec.delete();
                }

                return var8;
            }
        }
    }

    public int addVideoStream(int inputIndex, int streamId, ICodec codec, int width, int height) {
        return this.addVideoStream(inputIndex, streamId, codec, null, width, height);
    }

    public int addVideoStream(int inputIndex, int streamId, ICodec codec, IRational frameRate, int width, int height) {
        if (width > 0 && height > 0) {
            IStream stream = this.establishStream(inputIndex, streamId, codec);
            IStreamCoder coder = stream.getStreamCoder();

            try {
                List<IRational> supportedFrameRates = codec.getSupportedVideoFrameRates();
                IRational timeBase = null;
                if (supportedFrameRates != null && supportedFrameRates.size() > 0) {
                    IRational highestResolution = null;
                    Iterator i$ = supportedFrameRates.iterator();

                    while (i$.hasNext()) {
                        IRational supportedRate = (IRational) i$.next();
                        if (IRational.positive(supportedRate)) {
                            if (highestResolution == null) {
                                highestResolution = supportedRate.copyReference();
                            }

                            if (IRational.positive(frameRate)) {
                                if (supportedRate.compareTo(frameRate) == 0) {
                                    highestResolution = frameRate.copyReference();
                                }
                            } else if (highestResolution.getDouble() < supportedRate.getDouble()) {
                                highestResolution.delete();
                                highestResolution = supportedRate.copyReference();
                            }

                            supportedRate.delete();
                        }
                    }

                    if (IRational.positive(frameRate) && (highestResolution == null || highestResolution.compareTo(frameRate) != 0)) {
                        throw new UnsupportedOperationException("container does not support encoding at given frame rate: " + frameRate);
                    }

                    if (highestResolution == null) {
                        throw new UnsupportedOperationException("could not find supported frame rate for container: " + this.getUrl());
                    }

                    if (timeBase == null) {
                        timeBase = IRational.make(highestResolution.getDenominator(), highestResolution.getNumerator());
                    }

                    highestResolution.delete();
                    highestResolution = null;
                }

                if (IRational.positive(frameRate) && timeBase == null) {
                    timeBase = IRational.make(frameRate.getDenominator(), frameRate.getNumerator());
                }

                if (timeBase == null) {
                    timeBase = this.getDefaultTimebase();
                    if (codec.getID() == ID.CODEC_ID_MPEG4 && timeBase.getDenominator() > 65535) {
                        timeBase.delete();
                        timeBase = IRational.make(1, 65535);
                    }
                }

                coder.setTimeBase(timeBase);
                timeBase.delete();
                timeBase = null;
                coder.setWidth(width);
                coder.setHeight(height);
                coder.setPixelType(DEFAULT_PIXEL_TYPE);
                this.addStream(stream, inputIndex, stream.getIndex());
            } finally {
                coder.delete();
            }

            return stream.getIndex();
        } else {
            throw new IllegalArgumentException("invalid video frame size [" + width + " x " + height + "]");
        }
    }

    public int addVideoStreamWithBitRate(int inputIndex, int streamId, ID codecId, IRational frameRate, int bitrate, int width, int height) {
        if (width > 0 && height > 0) {
            ICodec codec = ICodec.findEncodingCodec(codecId);
            IStream stream = this.establishStream(inputIndex, streamId, codec);
            IStreamCoder coder = stream.getStreamCoder();

            try {
                List<IRational> supportedFrameRates = codec.getSupportedVideoFrameRates();
                IRational timeBase = null;
                if (supportedFrameRates != null && supportedFrameRates.size() > 0) {
                    IRational highestResolution = null;
                    Iterator i$ = supportedFrameRates.iterator();

                    while (i$.hasNext()) {
                        IRational supportedRate = (IRational) i$.next();
                        if (IRational.positive(supportedRate)) {
                            if (highestResolution == null) {
                                highestResolution = supportedRate.copyReference();
                            }

                            if (IRational.positive(frameRate)) {
                                if (supportedRate.compareTo(frameRate) == 0) {
                                    highestResolution = frameRate.copyReference();
                                }
                            } else if (highestResolution.getDouble() < supportedRate.getDouble()) {
                                highestResolution.delete();
                                highestResolution = supportedRate.copyReference();
                            }

                            supportedRate.delete();
                        }
                    }

                    if (IRational.positive(frameRate) && (highestResolution == null || highestResolution.compareTo(frameRate) != 0)) {
                        throw new UnsupportedOperationException("container does not support encoding at given frame rate: " + frameRate);
                    }

                    if (highestResolution == null) {
                        throw new UnsupportedOperationException("could not find supported frame rate for container: " + this.getUrl());
                    }

                    if (timeBase == null) {
                        timeBase = IRational.make(highestResolution.getDenominator(), highestResolution.getNumerator());
                    }

                    highestResolution.delete();
                    highestResolution = null;
                }

                if (IRational.positive(frameRate) && timeBase == null) {
                    timeBase = IRational.make(frameRate.getDenominator(), frameRate.getNumerator());
                }

                if (timeBase == null) {
                    timeBase = this.getDefaultTimebase();
                    if (codec.getID() == ID.CODEC_ID_MPEG4 && timeBase.getDenominator() > 65535) {
                        timeBase.delete();
                        timeBase = IRational.make(1, 65535);
                    }
                }

                coder.setTimeBase(timeBase);
                timeBase.delete();
                timeBase = null;
                coder.setWidth(width);
                coder.setBitRate(bitrate);
                coder.setHeight(height);
                coder.setPixelType(DEFAULT_PIXEL_TYPE);
                this.addStream(stream, inputIndex, stream.getIndex());
            } finally {
                coder.delete();
            }

            return stream.getIndex();
        } else {
            throw new IllegalArgumentException("invalid video frame size [" + width + " x " + height + "]");
        }
    }

    private IStream establishStream(int inputIndex, int streamId, ICodec codec) {
        if (inputIndex < 0) {
            throw new IllegalArgumentException("invalid input index " + inputIndex);
        } else if (streamId < 0) {
            throw new IllegalArgumentException("invalid stream id " + streamId);
        } else if (null == codec) {
            throw new IllegalArgumentException("null codec");
        } else {
            if (!this.isOpen()) {
                this.open();
            }

            IStream stream = this.getContainer().addNewStream(codec);
            if (stream == null) {
                throw new RuntimeException("Unable to create stream id " + streamId + ", index " + inputIndex + ", codec " + codec);
            } else {
                this.setForceInterleave(this.getContainer().getNumStreams() != 1);
                return stream;
            }
        }
    }

    public void setMaskLateStreamExceptions(boolean maskLateStreamExceptions) {
        this.mMaskLateStreamException = maskLateStreamExceptions;
    }

    public boolean willMaskLateStreamExceptions() {
        return this.mMaskLateStreamException;
    }

    public void setForceInterleave(boolean forceInterleave) {
        this.mForceInterleave = forceInterleave;
    }

    public boolean willForceInterleave() {
        return this.mForceInterleave;
    }

    public Integer getOutputStreamIndex(int inputStreamIndex) {
        return this.mOutputStreamIndices.get(inputStreamIndex);
    }

    private void encodeVideo(int streamIndex, IVideoPicture picture, BufferedImage image) {
        if (null == picture) {
            throw new IllegalArgumentException("no picture");
        } else {
            IStream stream = this.getStream(streamIndex);
            if (null != stream) {
                Integer outputIndex = this.getOutputStreamIndex(streamIndex);
                if (null == outputIndex) {
                    throw new IllegalArgumentException("unknow stream index: " + streamIndex);
                } else if (com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_VIDEO != this.mStreams.get(outputIndex).getStreamCoder().getCodecType()) {
                    throw new IllegalArgumentException("stream[" + streamIndex + "] is not video");
                } else {
                    IPacket packet = IPacket.make();

                    try {
                        if (stream.getStreamCoder().encodeVideo(packet, picture, 0) < 0) {
                            throw new RuntimeException("failed to encode video");
                        }

                        if (packet.isComplete()) {
                            this.writePacket(packet);
                        }
                    } finally {
                        if (packet != null) {
                            packet.delete();
                        }

                    }

                    super.onVideoPicture(new VideoPictureEvent(this, picture, image, picture.getTimeStamp(), TimeUnit.MICROSECONDS, streamIndex));
                }
            }
        }
    }

    public void encodeVideo(int streamIndex, IVideoPicture picture) {
        this.encodeVideo(streamIndex, picture, null);
    }

    public void encodeVideo(int streamIndex, BufferedImage image, long timeStamp, TimeUnit timeUnit) {
        if (null == image) {
            throw new IllegalArgumentException("NULL input image");
        } else if (null == timeUnit) {
            throw new IllegalArgumentException("NULL time unit");
        } else {
            IStream stream = this.getStream(streamIndex);
            if (null != stream) {
                IVideoPicture picture = this.convertToPicture(streamIndex, image, TimeUnit.MICROSECONDS.convert(timeStamp, timeUnit));

                try {
                    this.encodeVideo(streamIndex, picture, image);
                } finally {
                    if (picture != null) {
                        picture.delete();
                    }

                }

            }
        }
    }

    public void encodeAudio(int streamIndex, IAudioSamples samples) {
        if (null == samples) {
            throw new IllegalArgumentException("NULL input samples");
        } else {
            IStream stream = this.getStream(streamIndex);
            if (null != stream) {
                IStreamCoder coder = stream.getStreamCoder();

                try {
                    if (com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_AUDIO != coder.getCodecType()) {
                        throw new IllegalArgumentException("stream[" + streamIndex + "] is not audio");
                    } else {
                        int consumed = 0;

                        while ((long) consumed < samples.getNumSamples()) {
                            IPacket packet = IPacket.make();

                            try {
                                int result = coder.encodeAudio(packet, samples, (long) consumed);
                                if (result < 0) {
                                    throw new RuntimeException("failed to encode audio");
                                }

                                consumed += result;
                                if (packet.isComplete()) {
                                    this.writePacket(packet);
                                }
                            } finally {
                                if (packet != null) {
                                    packet.delete();
                                }

                            }
                        }

                        super.onAudioSamples(new AudioSamplesEvent(this, samples, streamIndex));
                    }
                } finally {
                    if (coder != null) {
                        coder.delete();
                    }

                }
            }
        }
    }

    public void encodeAudio(int streamIndex, short[] samples, long timeStamp, TimeUnit timeUnit) {
        if (null == samples) {
            throw new IllegalArgumentException("NULL input samples");
        } else {
            IStream stream = this.getStream(streamIndex);
            if (null != stream) {
                IStreamCoder coder = stream.getStreamCoder();

                try {
                    if (Format.FMT_S16 != coder.getSampleFormat()) {
                        throw new IllegalArgumentException("stream[" + streamIndex + "] is not 16 bit audio");
                    }

                    long sampleCount = (long) (samples.length / coder.getChannels());
                    IAudioSamples audioFrame = IAudioSamples.make(sampleCount, (long) coder.getChannels());
                    long timeStampMicro;
                    if (timeUnit == null) {
                        timeStampMicro = Global.NO_PTS;
                    } else {
                        timeStampMicro = TimeUnit.MICROSECONDS.convert(timeStamp, timeUnit);
                    }

                    audioFrame.setComplete(true, sampleCount, coder.getSampleRate(), coder.getChannels(), coder.getSampleFormat(), timeStampMicro);
                    audioFrame.put(samples, 0, 0, samples.length);
                    this.encodeAudio(streamIndex, audioFrame);
                } finally {
                    if (coder != null) {
                        coder.delete();
                    }

                }

            }
        }
    }

    public void encodeAudio(int streamIndex, short[] samples) {
        this.encodeAudio(streamIndex, samples, Global.NO_PTS, null);
    }

    private IVideoPicture convertToPicture(int streamIndex, BufferedImage image, long timeStamp) {
        IConverter videoConverter = this.mVideoConverters.get(streamIndex);
        if (videoConverter == null) {
            IStream stream = this.mStreams.get(streamIndex);
            IStreamCoder coder = stream.getStreamCoder();
            videoConverter = ConverterFactory.createConverter(ConverterFactory.findDescriptor(image), coder.getPixelType(), coder.getWidth(), coder.getHeight(), image.getWidth(), image.getHeight());
            this.mVideoConverters.put(streamIndex, videoConverter);
        }

        return videoConverter.toPicture(image, timeStamp);
    }

    private IStream getStream(int inputStreamIndex) {
        if (!this.isOpen()) {
            this.open();
        }

        int i;
        if (null == this.getOutputStreamIndex(inputStreamIndex)) {
            if (this.getContainer().isHeaderWritten()) {
                if (this.willMaskLateStreamExceptions()) {
                    return null;
                }

                throw new RuntimeException("Input stream index " + inputStreamIndex + " has not been seen before, but the media header has already been " + "written.  To mask these exceptions call setMaskLateStreamExceptions()");
            }

            if (null == this.mInputContainer) {
                throw new UnsupportedOperationException("MediaWriterMod can not yet create streams without an input container.");
            }

            if (!this.mInputContainer.isOpened()) {
                throw new RuntimeException("Can't get stream information from a closed input IContainer.");
            }

            for (i = 0; i < this.mInputContainer.getNumStreams(); ++i) {
                if (null == this.mOutputStreamIndices.get(i)) {
                    this.addStreamFromContainer(i);
                }
            }
        }

        if (!this.getContainer().isHeaderWritten()) {
            Iterator i$ = this.mStreams.values().iterator();

            while (i$.hasNext()) {
                IStream stream = (IStream) i$.next();
                if (!stream.getStreamCoder().isOpen()) {
                    this.openStream(stream);
                }
            }

            i = this.getContainer().writeHeader();
            if (0 != i) {
                throw new RuntimeException("Error " + IError.make(i) + ", failed to write header to container " + this.getContainer() + " while establishing stream " + this.mStreams.get(this.getOutputStreamIndex(inputStreamIndex)));
            }

            super.onWriteHeader(new WriteHeaderEvent(this));
        }

        IStream stream = this.mStreams.get(this.getOutputStreamIndex(inputStreamIndex));
        if (null == stream) {
            throw new RuntimeException("invalid input stream index (no stream): " + inputStreamIndex);
        } else {
            IStreamCoder coder = stream.getStreamCoder();
            if (null == coder) {
                throw new RuntimeException("invalid input stream index (no coder): " + inputStreamIndex);
            } else {
                return stream;
            }
        }
    }

    public boolean isSupportedCodecType(com.xuggle.xuggler.ICodec.Type type) {
        return com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_VIDEO == type || com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_AUDIO == type;
    }

    private boolean addStreamFromContainer(int inputStreamIndex) {
        IStream inputStream = this.mInputContainer.getStream((long) inputStreamIndex);
        IStreamCoder inputCoder = inputStream.getStreamCoder();
        com.xuggle.xuggler.ICodec.Type inputType = inputCoder.getCodecType();
        ID inputID = inputCoder.getCodecID();
        if (!this.isSupportedCodecType(inputType)) {
            return false;
        } else {
            IContainerFormat format = this.getContainer().getContainerFormat();
            switch (inputType) {
                case CODEC_TYPE_AUDIO:
                    this.addAudioStream(inputStream.getIndex(), inputStream.getId(), format.establishOutputCodecId(inputID), inputCoder.getChannels(), inputCoder.getSampleRate());
                    break;
                case CODEC_TYPE_VIDEO:
                    this.addVideoStream(inputStream.getIndex(), inputStream.getId(), format.establishOutputCodecId(inputID), inputCoder.getFrameRate(), inputCoder.getWidth(), inputCoder.getHeight());
            }

            return true;
        }
    }

    private void addStream(IStream stream, int inputStreamIndex, int outputStreamIndex) {
        this.mOutputStreamIndices.put(inputStreamIndex, outputStreamIndex);
        this.mStreams.put(outputStreamIndex, stream);
        IStreamCoder coder = stream.getStreamCoder();
        if (com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_VIDEO == coder.getCodecType()) {
            coder.setFlag(Flags.FLAG_QSCALE, true);
        }

        super.onAddStream(new AddStreamEvent(this, outputStreamIndex));
    }

    private void openStream(IStream stream) {
        IStreamCoder coder = stream.getStreamCoder();

        try {
            com.xuggle.xuggler.ICodec.Type type = coder.getCodecType();
            if (!coder.isOpen() && this.isSupportedCodecType(type)) {
                int rv = coder.open(null, null);
                if (rv < 0) {
                    throw new RuntimeException("could not open stream " + stream + ": " + getErrorMessage(rv));
                }

                this.mOpenedStreams.add(stream);
                super.onOpenCoder(new OpenCoderEvent(this, stream.getIndex()));
            }
        } finally {
            coder.delete();
        }

    }

    private void writePacket(IPacket packet) {
        if (this.getContainer().writePacket(packet, this.mForceInterleave) < 0) {
            throw new RuntimeException("failed to write packet: " + packet);
        } else {
            super.onWritePacket(new WritePacketEvent(this, packet));
        }
    }

    public void flush() {
        Iterator i$ = this.mStreams.values().iterator();

        while (true) {
            while (true) {
                IStreamCoder coder;
                do {
                    if (!i$.hasNext()) {
                        this.getContainer().flushPackets();
                        super.onFlush(new FlushEvent(this));
                        return;
                    }

                    IStream stream = (IStream) i$.next();
                    coder = stream.getStreamCoder();
                } while (!coder.isOpen());

                IPacket packet;
                if (com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_AUDIO == coder.getCodecType()) {
                    for (packet = IPacket.make(); coder.encodeAudio(packet, null, 0L) >= 0 && packet.isComplete(); packet = IPacket.make()) {
                        this.writePacket(packet);
                        packet.delete();
                    }

                    packet.delete();
                } else if (com.xuggle.xuggler.ICodec.Type.CODEC_TYPE_VIDEO == coder.getCodecType()) {
                    for (packet = IPacket.make(); coder.encodeVideo(packet, null, 0) >= 0 && packet.isComplete(); packet = IPacket.make()) {
                        this.writePacket(packet);
                        packet.delete();
                    }

                    packet.delete();
                }
            }
        }
    }

    public void open() {
        if (this.getContainer().open(this.getUrl(), com.xuggle.xuggler.IContainer.Type.WRITE, this.mContainerFormat, true, false) < 0) {
            throw new IllegalArgumentException("could not open: " + this.getUrl());
        } else {
            super.onOpen(new OpenEvent(this));
            this.setShouldCloseContainer(true);
        }
    }

    public void close() {
        this.flush();
        int rv;
        if ((rv = this.getContainer().writeTrailer()) < 0) {
            throw new RuntimeException("error " + IError.make(rv) + ", failed to write trailer to " + this.getUrl());
        } else {
            super.onWriteTrailer(new WriteTrailerEvent(this));
            Iterator i$ = this.mOpenedStreams.iterator();

            while (i$.hasNext()) {
                IStream stream = (IStream) i$.next();
                IStreamCoder coder = stream.getStreamCoder();

                try {
                    if ((rv = coder.close()) < 0) {
                        throw new RuntimeException("error " + getErrorMessage(rv) + ", failed close coder " + coder);
                    }

                    super.onCloseCoder(new CloseCoderEvent(this, stream.getIndex()));
                } finally {
                    coder.delete();
                }
            }

            this.mStreams.clear();
            this.mOpenedStreams.clear();
            this.mVideoConverters.clear();
            if (this.getShouldCloseContainer()) {
                if ((rv = this.getContainer().close()) < 0) {
                    throw new RuntimeException("error " + IError.make(rv) + ", failed close IContainer " + this.getContainer() + " for " + this.getUrl());
                }

                this.setShouldCloseContainer(false);
            }

            super.onClose(new CloseEvent(this));
        }
    }

    public Type getDefaultPixelType() {
        return DEFAULT_PIXEL_TYPE;
    }

    public Format getDefaultSampleFormat() {
        return DEFAULT_SAMPLE_FORMAT;
    }

    public IRational getDefaultTimebase() {
        return DEFAULT_TIMEBASE.copyReference();
    }

    public String toString() {
        return "MediaWriterMod[" + this.getUrl() + "]";
    }

    public void onOpen(IOpenEvent event) {
    }

    public void onClose(ICloseEvent event) {
        if (this.isOpen()) {
            this.close();
        }

    }

    public void onAddStream(IAddStreamEvent event) {
    }

    public void onOpenCoder(IOpenCoderEvent event) {
    }

    public void onCloseCoder(ICloseCoderEvent event) {
    }

    public void onVideoPicture(IVideoPictureEvent event) {
        if (event.getImage() != null) {
            this.encodeVideo(event.getStreamIndex(), event.getImage(), event.getTimeStamp(event.getTimeUnit()), event.getTimeUnit());
        } else {
            this.encodeVideo(event.getStreamIndex(), event.getPicture());
        }

    }

    public void onAudioSamples(IAudioSamplesEvent event) {
        this.encodeAudio(event.getStreamIndex(), event.getAudioSamples());
    }

    public void onReadPacket(IReadPacketEvent event) {
    }

    public void onWritePacket(IWritePacketEvent event) {
    }

    public void onWriteHeader(IWriteHeaderEvent event) {
    }

    public void onFlush(IFlushEvent event) {
    }

    public void onWriteTrailer(IWriteTrailerEvent event) {
    }
}

