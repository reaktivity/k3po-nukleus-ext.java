/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.k3po.nukleus.ext.internal.behavior;

import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.future;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusFlags.RST;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusTransmission.DUPLEX;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusTransmission.SIMPLEX;

import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.layout.StreamsLayout;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.ListFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.AckFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.BeginFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.FrameFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.RegionFW;
import org.reaktivity.k3po.nukleus.ext.internal.util.function.LongLongFunction;
import org.reaktivity.k3po.nukleus.ext.internal.util.function.LongObjectBiConsumer;

final class NukleusPartition implements AutoCloseable
{
    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();

    private final AckFW.Builder ackRW = new AckFW.Builder();

    private final Path partitionPath;
    private final StreamsLayout layout;
    private final RingBuffer streamsBuffer;
    private final RingBuffer throttleBuffer;
    private final LongLongFunction<NukleusServerChannel> lookupRoute;
    private final LongFunction<MessageHandler> lookupStream;
    private final MessageHandler streamHandler;
    private final LongObjectBiConsumer<MessageHandler> registerStream;
    private final MutableDirectBuffer writeBuffer;
    private final NukleusStreamFactory streamFactory;
    private final LongFunction<NukleusCorrelation> correlateEstablished;
    private final BiFunction<String, String, NukleusTarget> supplyTarget;

    NukleusPartition(
        Path partitionPath,
        StreamsLayout layout,
        LongLongFunction<NukleusServerChannel> lookupRoute,
        LongFunction<MessageHandler> lookupStream,
        LongObjectBiConsumer<MessageHandler> registerStream,
        MutableDirectBuffer writeBuffer,
        NukleusStreamFactory streamFactory,
        LongFunction<NukleusCorrelation> correlateEstablished,
        BiFunction<String, String, NukleusTarget> supplyTarget)
    {
        this.partitionPath = partitionPath;
        this.layout = layout;
        this.streamsBuffer = layout.streamsBuffer();
        this.throttleBuffer = layout.throttleBuffer();
        this.writeBuffer = writeBuffer;

        this.lookupRoute = lookupRoute;
        this.lookupStream = lookupStream;
        this.registerStream = registerStream;
        this.streamHandler = this::handleStream;
        this.streamFactory = streamFactory;
        this.correlateEstablished = correlateEstablished;
        this.supplyTarget = supplyTarget;
    }

    public int process()
    {
        return streamsBuffer.read(streamHandler);
    }

    @Override
    public void close()
    {
        layout.close();
    }

    @Override
    public String toString()
    {
        return String.format("%s [%s]", getClass().getSimpleName(), partitionPath);
    }

    private void handleStream(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        frameRO.wrap(buffer, index, index + length);

        final long streamId = frameRO.streamId();

        final MessageHandler handler = lookupStream.apply(streamId);

        if (handler != null)
        {
            handler.onMessage(msgTypeId, buffer, index, length);
        }
        else
        {
            handleUnrecognized(msgTypeId, buffer, index, length);
        }
    }

    private void handleUnrecognized(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        if (msgTypeId == BeginFW.TYPE_ID)
        {
            final BeginFW begin = beginRO.wrap(buffer, index, index + length);
            handleBegin(begin);
        }
        else
        {
            frameRO.wrap(buffer, index, index + length);

            final long streamId = frameRO.streamId();

            doAck(streamId, RST.flag());
        }
    }

    private void handleBegin(
        BeginFW begin)
    {
        final long sourceRef = begin.sourceRef();
        final long sourceId = begin.streamId();
        final NukleusServerChannel serverChannel = lookupRoute.apply(sourceRef, begin.authorization());

        if (serverChannel != null)
        {
            handleBeginInitial(begin, serverChannel);
        }
        else
        {
            if (sourceRef == 0L)
            {
                handleBeginReply(begin);
            }
            else
            {
                doAck(sourceId, RST.flag());
            }
        }
    }

    private void handleBeginInitial(
        final BeginFW begin,
        final NukleusServerChannel serverChannel)
    {
        final long sourceId = begin.streamId();
        final long correlationId = begin.correlationId();
        NukleusChildChannel childChannel = doAccept(serverChannel, correlationId);

        final ChannelFuture handshakeFuture = future(childChannel);
        final MessageHandler newStream = streamFactory.newStream(childChannel, this, handshakeFuture);
        registerStream.accept(sourceId, newStream);
        newStream.onMessage(begin.typeId(), (MutableDirectBuffer) begin.buffer(), begin.offset(), begin.sizeof());

        fireChannelBound(childChannel, childChannel.getLocalAddress());

        NukleusChannelConfig childConfig = childChannel.getConfig();
        NukleusChannelAddress remoteAddress = childChannel.getRemoteAddress();
        String remoteName = remoteAddress.getReceiverName();
        String partitionName = childConfig.getWritePartition();
        NukleusTarget remoteTarget = supplyTarget.apply(remoteName, partitionName);

        if (childConfig.getTransmission() == DUPLEX)
        {
            remoteTarget.doBeginReply(childChannel, handshakeFuture);
        }

        fireChannelConnected(childChannel, childChannel.getRemoteAddress());
    }

    private void handleBeginReply(
        final BeginFW begin)
    {
        final long correlationId = begin.correlationId();
        final long sourceId = begin.streamId();
        final NukleusCorrelation correlation = correlateEstablished.apply(correlationId);

        if (correlation != null)
        {
            final ChannelFuture handshakeFuture = correlation.correlatedFuture();
            final NukleusClientChannel clientChannel = (NukleusClientChannel) handshakeFuture.getChannel();
            final MessageHandler newStream = streamFactory.newStream(clientChannel, this, handshakeFuture);
            registerStream.accept(sourceId, newStream);
            newStream.onMessage(begin.typeId(), (MutableDirectBuffer) begin.buffer(), begin.offset(), begin.sizeof());
        }
        else
        {
            doAck(sourceId, RST.flag());
        }
    }

    void doAck(
        final long streamId,
        final int flags)
    {
        final AckFW ack = ackRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .streamId(streamId)
                .flags(flags)
                .build();

        throttleBuffer.write(ack.typeId(), ack.buffer(), ack.offset(), ack.sizeof());
    }

    void doAck(
        final long streamId,
        final int flags,
        final ListFW<RegionFW> regions)
    {
        final AckFW ack = ackRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .streamId(streamId)
            .flags(flags)
            .regions(b -> regions.forEach(e -> b.item(m -> m.address(e.address()).length(e.length()).streamId(e.streamId()))))
            .build();

        throttleBuffer.write(ack.typeId(), ack.buffer(), ack.offset(), ack.sizeof());
    }

    private NukleusChildChannel doAccept(
        NukleusServerChannel serverChannel,
        long correlationId)
    {
        try
        {
            NukleusServerChannelConfig serverConfig = serverChannel.getConfig();
            ChannelPipelineFactory pipelineFactory = serverConfig.getPipelineFactory();
            ChannelPipeline pipeline = pipelineFactory.getPipeline();

            final NukleusChannelAddress serverAddress = serverChannel.getLocalAddress();
            NukleusChannelAddress remoteAddress = serverAddress.newReplyToAddress();

            // fire child serverChannel opened
            ChannelFactory channelFactory = serverChannel.getFactory();
            NukleusChildChannelSink childSink = new NukleusChildChannelSink();
            NukleusChildChannel childChannel =
                  new NukleusChildChannel(serverChannel, channelFactory, pipeline, childSink, serverChannel.reaktor);

            NukleusChannelConfig childConfig = childChannel.getConfig();
            childConfig.setBufferFactory(serverConfig.getBufferFactory());
            childConfig.setTransmission(serverConfig.getTransmission());
            childConfig.setReadPartition(serverConfig.getReadPartition());
            childConfig.setWritePartition(serverConfig.getWritePartition());

            childConfig.setCorrelation(correlationId);

            if (childConfig.getTransmission() == SIMPLEX)
            {
                childChannel.setWriteClosed();
            }
            else
            {
                childChannel.acquireWriteMemory();
            }

            String partitionName = childConfig.getWritePartition();
            if (partitionName == null)
            {
                String senderName = remoteAddress.getSenderName();
                childConfig.setWritePartition(senderName);
            }

            childChannel.setLocalAddress(serverAddress);
            childChannel.setRemoteAddress(remoteAddress);

            return childChannel;
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        // unreachable
        return null;
    }
}
