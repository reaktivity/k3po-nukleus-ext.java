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
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelInterestChanged;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireWriteComplete;
import static org.jboss.netty.channel.Channels.succeededFuture;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireFlushed;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireOutputAborted;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireOutputShutdown;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.BEGIN;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.TRANSFER;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusFlags.FIN;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusFlags.RST;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NullChannelBuffer.NULL_BUFFER;

import java.nio.file.Path;
import java.util.Deque;
import java.util.Random;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.kaazing.k3po.driver.internal.netty.channel.ChannelAddress;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.layout.Layout;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.layout.StreamsLayout;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.ListFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.AckFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.BeginFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.FrameFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.RegionFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.TransferFW;
import org.reaktivity.k3po.nukleus.ext.internal.util.function.LongObjectBiConsumer;

final class NukleusTarget implements AutoCloseable
{
    private final FrameFW frameRO = new FrameFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final TransferFW.Builder transferRW = new TransferFW.Builder();

    private final AckFW ackRO = new AckFW();
    private final AckFW.Builder ackRW = new AckFW.Builder();

    private final Path partitionPath;
    private final Layout layout;
    private final RingBuffer streamsBuffer;
    private final RingBuffer throttleBuffer;
    private final LongFunction<MessageHandler> lookupThrottle;
    private final MessageHandler throttleHandler;
    private final LongObjectBiConsumer<MessageHandler> registerThrottle;
    private final LongConsumer unregisterThrottle;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer ackBuffer;
    private final LongObjectBiConsumer<NukleusCorrelation> correlateNew;

    NukleusTarget(
        Path partitionPath,
        StreamsLayout layout,
        MutableDirectBuffer writeBuffer,
        LongFunction<MessageHandler> lookupThrottle,
        LongObjectBiConsumer<MessageHandler> registerThrottle,
        LongConsumer unregisterThrottle,
        LongObjectBiConsumer<NukleusCorrelation> correlateNew)
    {
        this.partitionPath = partitionPath;
        this.layout = layout;
        this.streamsBuffer = layout.streamsBuffer();
        this.throttleBuffer = layout.throttleBuffer();
        this.writeBuffer = writeBuffer;
        this.ackBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);

        this.lookupThrottle = lookupThrottle;
        this.registerThrottle = registerThrottle;
        this.unregisterThrottle = unregisterThrottle;
        this.correlateNew = correlateNew;
        this.throttleHandler = this::handleThrottle;
    }

    public int process()
    {
        return throttleBuffer.read(throttleHandler);
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

    public void doConnect(
        NukleusClientChannel clientChannel,
        NukleusChannelAddress remoteAddress,
        ChannelFuture connectFuture)
    {
        try
        {
            final String senderName = remoteAddress.getSenderName();
            final long routeRef = remoteAddress.getRoute();
            final long streamId = clientChannel.targetId();
            final long correlationId = new Random().nextLong();
            ChannelFuture handshakeFuture = succeededFuture(clientChannel);

            NukleusChannelConfig clientConfig = clientChannel.getConfig();
            switch (clientConfig.getTransmission())
            {
            case DUPLEX:
                ChannelFuture correlatedFuture = clientChannel.beginInputFuture();
                correlateNew.accept(correlationId, new NukleusCorrelation(clientChannel, correlatedFuture));
                handshakeFuture = correlatedFuture;
                break;
            case HALF_DUPLEX:
                ChannelFuture replyFuture = clientChannel.beginInputFuture();
                correlateNew.accept(correlationId, new NukleusCorrelation(clientChannel, replyFuture));
                replyFuture.addListener(f -> fireChannelInterestChanged(f.getChannel()));
                break;
            default:
                break;
            }
            long authorization = remoteAddress.getAuthorization();
            clientChannel.targetAuth(authorization);

            ChannelBuffer beginExt = clientChannel.writeExtBuffer(BEGIN, true);
            final int writableExtBytes = beginExt.readableBytes();
            final byte[] beginExtCopy = writeExtCopy(beginExt);

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                   .streamId(streamId)
                   .authorization(authorization)
                   .source(senderName)
                   .sourceRef(routeRef)
                   .correlationId(correlationId)
                   .extension(p -> p.set(beginExtCopy))
                   .build();

            if (!clientChannel.isBound())
            {
                ChannelAddress localAddress = remoteAddress.newReplyToAddress();
                clientChannel.setLocalAddress(localAddress);
                clientChannel.setBound();
                fireChannelBound(clientChannel, localAddress);
            }

            clientChannel.setRemoteAddress(remoteAddress);

            handshakeFuture.addListener(new ChannelFutureListener()
            {
                @Override
                public void operationComplete(
                    ChannelFuture future) throws Exception
                {
                    if (future.isSuccess())
                    {
                        clientChannel.setConnected();

                        connectFuture.setSuccess();
                        fireChannelConnected(clientChannel, clientChannel.getRemoteAddress());
                    }
                    else
                    {
                        connectFuture.setFailure(future.getCause());

                        // TODO: connectFuture.setFailure(...) should already be sufficient to fail connect pipeline
                        fireChannelDisconnected(clientChannel);
                    }
                }
            });

            final Throttle throttle = new Throttle(clientChannel, handshakeFuture);
            registerThrottle.accept(begin.streamId(), throttle::handleThrottle);

            streamsBuffer.write(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

            beginExt.skipBytes(writableExtBytes);
            beginExt.discardReadBytes();

            clientChannel.beginOutputFuture().setSuccess();
        }
        catch (Exception ex)
        {
            connectFuture.setFailure(ex);
        }
    }

    public void doBeginReply(
        NukleusChannel channel,
        ChannelFuture handshakeFuture)
    {
        final long correlationId = channel.getConfig().getCorrelation();
        final NukleusChannelAddress remoteAddress = channel.getRemoteAddress();
        final String senderName = remoteAddress.getSenderName();
        final ChannelBuffer beginExt = channel.writeExtBuffer(BEGIN, true);
        final int writableExtBytes = beginExt.readableBytes();
        final byte[] beginExtCopy = writeExtCopy(beginExt);

        final long streamId = channel.targetId();

        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .streamId(streamId)
                .source(senderName)
                .sourceRef(0L)
                .correlationId(correlationId)
                .extension(p -> p.set(beginExtCopy))
                .build();

        final Throttle throttle = new Throttle(channel, handshakeFuture);
        registerThrottle.accept(begin.streamId(), throttle::handleThrottle);

        streamsBuffer.write(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        beginExt.skipBytes(writableExtBytes);
        beginExt.discardReadBytes();

        channel.beginOutputFuture().setSuccess();
    }

    public void doWrite(
        NukleusChannel channel,
        MessageEvent newWriteRequest)
    {
        doFlushBegin(channel);

        channel.writeRequests.addLast(newWriteRequest);

        flushThrottledWrites(channel);
    }

    public void doFlush(
        NukleusChannel channel,
        ChannelFuture flushFuture)
    {
        doFlushBegin(channel);

        if (channel.writeExtBuffer(TRANSFER, true).readable())
        {
            if (channel.writeRequests.isEmpty())
            {
                Object message = NULL_BUFFER;
                MessageEvent newWriteRequest = new DownstreamMessageEvent(channel, flushFuture, message, null);
                channel.writeRequests.addLast(newWriteRequest);
            }
            flushThrottledWrites(channel);
        }
        else
        {
            flushFuture.setSuccess();
            fireFlushed(channel);
        }
    }

    public void doAbortOutput(
        NukleusChannel channel,
        ChannelFuture abortFuture)
    {
        doFlushBegin(channel);

        final long streamId = channel.targetId();
        long authorization = channel.targetAuth();

        final TransferFW transfer = transferRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .streamId(streamId)
                .authorization(authorization)
                .flags(RST.flag())
                .build();

        streamsBuffer.write(transfer.typeId(), transfer.buffer(), transfer.offset(), transfer.sizeof());

        abortFuture.setSuccess();
    }

    public void doShutdownOutput(
        NukleusChannel channel,
        ChannelFuture handlerFuture)
    {
        doFlushBegin(channel);

        final long streamId = channel.targetId();
        final long authorization = channel.targetAuth();

        final ChannelBuffer writeExt = channel.writeExtBuffer(TRANSFER, true);
        final int writableExtBytes = writeExt.readableBytes();
        final byte[] writeExtCopy = writeExtCopy(writeExt);

        final TransferFW transfer = transferRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .streamId(streamId)
                .authorization(authorization)
                .flags(FIN.flag())
                .extension(p -> p.set(writeExtCopy))
                .build();

        streamsBuffer.write(transfer.typeId(), transfer.buffer(), transfer.offset(), transfer.sizeof());

        writeExt.skipBytes(writableExtBytes);
        writeExt.discardReadBytes();

        fireOutputShutdown(channel);
        handlerFuture.setSuccess();

        if (channel.setWriteClosed())
        {
            fireChannelDisconnected(channel);
            fireChannelUnbound(channel);
            fireChannelClosed(channel);
        }
    }

    public void doClose(
        NukleusChannel channel,
        ChannelFuture handlerFuture)
    {
        doFlushBegin(channel);

        final long streamId = channel.targetId();
        final long authorization = channel.targetAuth();

        final TransferFW transfer = transferRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .streamId(streamId)
                .authorization(authorization)
                .flags(FIN.flag())
                .build();

        streamsBuffer.write(transfer.typeId(), transfer.buffer(), transfer.offset(), transfer.sizeof());

        handlerFuture.setSuccess();

        if (channel.setClosed())
        {
            fireChannelDisconnected(channel);
            fireChannelUnbound(channel);
            fireChannelClosed(channel);
        }
    }

    private boolean doFlushBegin(
        NukleusChannel channel)
    {
        final boolean doFlush = !channel.beginOutputFuture().isDone();

        if (doFlush)
        {
            // SERVER, HALF_DUPLEX
            ChannelFuture handshakeFuture = Channels.future(channel);
            doBeginReply(channel, handshakeFuture);
        }

        return doFlush;
    }

    private void flushThrottledWrites(
        NukleusChannel channel)
    {
        final Deque<MessageEvent> writeRequests = channel.writeRequests;
        final long authorization = channel.targetAuth();

        while (channel.writableBytes() > 0 && !writeRequests.isEmpty())
        {
            MessageEvent writeRequest = writeRequests.peekFirst();
            ChannelBuffer writeBuf = (ChannelBuffer) writeRequest.getMessage();
            ChannelBuffer transferExt = channel.writeExtBuffer(TRANSFER, true);

            if (writeBuf.readable() || transferExt.readable())
            {
                final boolean flushing = writeBuf == NULL_BUFFER;
                final int writeBytes = writeBuf.readableBytes();
                final int writableBytes = Math.min(channel.writableBytes(), writeBytes);

                // allow extension-only WRITE frames to be flushed immediately
                if (writableBytes > 0 || !writeBuf.readable())
                {
                    final int writableExtBytes = transferExt.readableBytes();
                    final byte[] transferExtCopy = writeExtCopy(transferExt);

                    final long streamId = channel.targetId();
                    final TransferFW transfer = transferRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                            .streamId(streamId)
                            .authorization(authorization)
                            .regions(rs -> channel.flushBytes(rs, writeBuf, writableBytes))
                            .extension(p -> p.set(transferExtCopy))
                            .build();

                    streamsBuffer.write(transfer.typeId(), transfer.buffer(), transfer.offset(), transfer.sizeof());

                    transferExt.skipBytes(writableExtBytes);
                    transferExt.discardReadBytes();

                    if (!writeBuf.readable())
                    {
                        writeRequests.removeFirst();
                        writeRequest.getFuture().setSuccess();
                    }
                }

                if (flushing)
                {
                    fireFlushed(channel);
                }
                else
                {
                    fireWriteComplete(channel, writeBytes);
                }
            }
        }
    }

    private byte[] writeExtCopy(
        ChannelBuffer writeExt)
    {
        final int writableExtBytes = writeExt.readableBytes();
        final byte[] writeExtArray = writeExt.array();
        final int writeExtArrayOffset = writeExt.arrayOffset();
        final int writeExtReaderIndex = writeExt.readerIndex();

        // TODO: avoid allocation
        final byte[] writeExtCopy = new byte[writableExtBytes];
        System.arraycopy(writeExtArray, writeExtArrayOffset + writeExtReaderIndex, writeExtCopy, 0, writeExtCopy.length);
        return writeExtCopy;
    }

    private void handleThrottle(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        frameRO.wrap(buffer, index, index + length);

        final long streamId = frameRO.streamId();

        final MessageHandler handler = lookupThrottle.apply(streamId);

        if (handler != null)
        {
            handler.onMessage(msgTypeId, buffer, index, length);
        }
    }

    private final class Throttle
    {
        private final NukleusChannel channel;
        private final ChannelFuture handshakeFuture;

        private Throttle(
            NukleusChannel channel,
            ChannelFuture handshakeFuture)
        {
            this.channel = channel;
            this.handshakeFuture = handshakeFuture;

            handshakeFuture.addListener(this::onHandshakeCompleted);
        }

        private void handleThrottle(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case AckFW.TYPE_ID:
                final AckFW ack = ackRO.wrap(buffer, index, index + length);
                onAck(ack);
                break;
            default:
                throw new IllegalArgumentException("Unexpected message type: " + msgTypeId);
            }
        }

        private void onAck(
            AckFW ack)
        {
            final int flags = ack.flags();
            final ListFW<RegionFW> regions = ack.regions();

            if (FIN.check(flags) || RST.check(flags))
            {
                final long streamId = ack.streamId();
                unregisterThrottle.accept(streamId);
            }

            if (RST.check(flags))
            {
                if (!channel.hasAcknowledged() && !handshakeFuture.isDone())
                {
                    handshakeFuture.setFailure(new ChannelException("Handshake failed"));
                }
                else if (channel.setWriteAborted())
                {
                    fireOutputAborted(channel);
                }
            }
            else
            {
                channel.acknowledge(regions);
                flushThrottledWrites(channel);
            }
        }

        private void onHandshakeCompleted(
            ChannelFuture future)
        {
            if (!future.isSuccess())
            {
                final long streamId = channel.sourceId();
                final AckFW ack = ackRW.wrap(ackBuffer, 0, ackBuffer.capacity())
                        .streamId(streamId)
                        .flags(RST.flag())
                        .build();

                onAck(ack);
            }
        }
    }
}
