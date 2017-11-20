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

import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireInputAborted;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireInputShutdown;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.BEGIN;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.DATA;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.END;

import java.util.function.LongConsumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.OctetsFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.AbortFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.BeginFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.DataFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.EndFW;

public final class NukleusStreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();

    private final LongConsumer unregisterStream;

    public NukleusStreamFactory(
        LongConsumer unregisterStream)
    {
        this.unregisterStream = unregisterStream;
    }

    public MessageHandler newStream(
        NukleusChannel channel,
        NukleusPartition partition,
        ChannelFuture handshakeFuture)
    {
        return new Stream(channel, partition, handshakeFuture)::handleStream;
    }

    private final class Stream
    {
        private final NukleusChannel channel;
        private final NukleusPartition partition;
        private final ChannelFuture handshakeFuture;

        private Stream(
            NukleusChannel channel,
            NukleusPartition partition,
            ChannelFuture handshakeFuture)
        {
            this.channel = channel;
            this.partition = partition;
            this.handshakeFuture = handshakeFuture;
        }

        private void handleStream(
            int msgTypeId,
            MutableDirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onBegin(begin);
                break;
            case DataFW.TYPE_ID:
                DataFW data = dataRO.wrap(buffer, index, index + length);
                onData(data);
                break;
            case EndFW.TYPE_ID:
                EndFW end = endRO.wrap(buffer, index, index + length);
                onEnd(end);
                break;
            case AbortFW.TYPE_ID:
                AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onAbort(abort);
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            final long streamId = begin.streamId();
            final OctetsFW beginExt = begin.extension();

            final NukleusChannelConfig channelConfig = channel.getConfig();
            final int initialWindow = channelConfig.getWindow();
            final int padding = channelConfig.getPadding();

            int beginExtBytes = beginExt.sizeof();
            if (beginExtBytes != 0)
            {
                final DirectBuffer buffer = beginExt.buffer();
                final int offset = beginExt.offset();

                // TODO: avoid allocation
                final byte[] beginExtCopy = new byte[beginExtBytes];
                buffer.getBytes(offset, beginExtCopy);

                channel.readExtBuffer(BEGIN).writeBytes(beginExtCopy);
            }

            channel.sourceId(streamId);
            channel.sourceAuth(begin.authorization());

            partition.doWindow(channel, initialWindow, padding);

            channel.beginInputFuture().setSuccess();

            handshakeFuture.setSuccess();
        }

        private void onData(
            DataFW data)
        {
            final long streamId = data.streamId();
            final OctetsFW payload = data.payload();
            final ChannelBuffer message = payload.get(this::readBuffer);
            final int readableBytes = message.readableBytes();
            final OctetsFW dataExt = data.extension();

            if (channel.readableBytes() >= readableBytes && data.authorization() == channel.sourceAuth())
            {
                channel.readableBytes(-readableBytes);

                int dataExtBytes = dataExt.sizeof();
                if (dataExtBytes != 0)
                {
                    final DirectBuffer buffer = dataExt.buffer();
                    final int offset = dataExt.offset();

                    // TODO: avoid allocation
                    final byte[] dataExtCopy = new byte[dataExtBytes];
                    buffer.getBytes(offset, dataExtCopy);

                    channel.readExtBuffer(DATA).writeBytes(dataExtCopy);
                }

                int padding = channel.getConfig().getPadding();
                int window = channel.getConfig().getWindow();
                partition.doWindow(channel, readableBytes + padding, padding);

                if (!channel.getConfig().getUpdate() && readableBytes >= window)
                {
                    partition.doReset(streamId);
                }
                fireMessageReceived(channel, message);
            }
            else
            {
                partition.doReset(streamId);
            }
        }

        private void onEnd(
            EndFW end)
        {
            final long streamId = end.streamId();
            if (end.authorization() != channel.sourceAuth())
            {
                partition.doReset(streamId);
            }
            unregisterStream.accept(streamId);

            final OctetsFW endExt = end.extension();

            int endExtBytes = endExt.sizeof();
            if (endExtBytes != 0)
            {
                final DirectBuffer buffer = endExt.buffer();
                final int offset = endExt.offset();

                // TODO: avoid allocation
                final byte[] endExtCopy = new byte[endExtBytes];
                buffer.getBytes(offset, endExtCopy);

                channel.readExtBuffer(END).writeBytes(endExtCopy);
            }

            if (channel.setReadClosed())
            {
                fireInputShutdown(channel);
                fireChannelDisconnected(channel);
                fireChannelUnbound(channel);
                fireChannelClosed(channel);
            }
            else
            {
                fireInputShutdown(channel);
            }
        }

        private void onAbort(
            AbortFW abort)
        {
            final long streamId = abort.streamId();
            if (abort.authorization() != channel.sourceAuth())
            {
                partition.doReset(streamId);
            }
            unregisterStream.accept(streamId);

            if (channel.setReadAborted())
            {
                fireInputAborted(channel);
            }
        }

        private ChannelBuffer readBuffer(
            DirectBuffer buffer,
            int index,
            int maxLimit)
        {
            // TODO: avoid allocation
            final byte[] array = new byte[maxLimit - index];
            buffer.getBytes(index, array);
            return channel.getConfig().getBufferFactory().getBuffer(array, 0, array.length);
        }
    }
}
