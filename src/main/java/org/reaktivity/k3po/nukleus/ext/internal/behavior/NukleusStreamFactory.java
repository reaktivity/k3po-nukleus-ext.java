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

import static java.nio.ByteBuffer.allocateDirect;
import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireMessageReceived;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireInputAborted;
import static org.kaazing.k3po.driver.internal.netty.channel.Channels.fireInputShutdown;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.BEGIN;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.TRANSFER;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusFlags.FIN;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusFlags.RST;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NullChannelBuffer.NULL_BUFFER;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.LongConsumer;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.ListFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.OctetsFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.BeginFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.RegionFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.TransferFW;

public final class NukleusStreamFactory
{
    private final BeginFW beginRO = new BeginFW();
    private final TransferFW transferRO = new TransferFW();

    private final LongUnaryOperator resolveMemory;
    private final LongConsumer unregisterStream;

    public NukleusStreamFactory(
        LongUnaryOperator resolveMemory,
        LongConsumer unregisterStream)
    {
        this.resolveMemory = resolveMemory;
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
            case TransferFW.TYPE_ID:
                TransferFW transfer = transferRO.wrap(buffer, index, index + length);
                onTransfer(transfer);
                break;
            }
        }

        private void onBegin(
            BeginFW begin)
        {
            final long streamId = begin.streamId();
            final long authorization = begin.authorization();
            final int flags = begin.flags();
            final OctetsFW beginExt = begin.extension();

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
            channel.sourceAuth(authorization);

            partition.doAck(streamId, flags);

            channel.beginInputFuture().setSuccess();

            handshakeFuture.setSuccess();
        }

        private void onTransfer(
            TransferFW transfer)
        {
            final long streamId = transfer.streamId();
            final ListFW<RegionFW> regions = transfer.regions();
            final OctetsFW transferExt = transfer.extension();
            final int flags = transfer.flags();

            final ByteOrder byteOrder = channel.getConfig().getBufferFactory().getDefaultOrder();
            final ChannelBuffer message = toChannelBuffer(byteOrder, flags, regions, transferExt);

            if (transfer.authorization() == channel.sourceAuth())
            {
                int transferExtBytes = transferExt.sizeof();
                if (transferExtBytes != 0)
                {
                    final DirectBuffer buffer = transferExt.buffer();
                    final int offset = transferExt.offset();

                    // TODO: avoid allocation
                    final byte[] transferExtCopy = new byte[transferExtBytes];
                    buffer.getBytes(offset, transferExtCopy);

                    channel.readExtBuffer(TRANSFER).writeBytes(transferExtCopy);
                }

                if (message != null)
                {
                    fireMessageReceived(channel, message);
                }
            }

            int ackFlags = flags;

            if (RST.check(flags))
            {
                if (transfer.authorization() != channel.sourceAuth())
                {
                    ackFlags = RST.set(ackFlags);
                }

                unregisterStream.accept(streamId);

                if (channel.setReadAborted())
                {
                    if (channel.setReadClosed())
                    {
                        fireInputAborted(channel);
                        fireChannelDisconnected(channel);
                        fireChannelUnbound(channel);
                        fireChannelClosed(channel);
                    }
                    else
                    {
                        fireInputAborted(channel);
                    }
                }

            }
            else if (FIN.check(flags))
            {
                if (transfer.authorization() != channel.sourceAuth())
                {
                    ackFlags = RST.set(ackFlags);
                }

                unregisterStream.accept(streamId);

                if (channel.setReadClosed())
                {
                    fireInputShutdown(channel);
                    fireChannelDisconnected(channel);
                    fireChannelUnbound(channel);
                    fireChannelClosed(channel);
                }
                else if (!channel.isClosing())
                {
                    fireInputShutdown(channel);
                }
            }

            partition.doAck(streamId, ackFlags, regions);
        }

        private ChannelBuffer toChannelBuffer(
            ByteOrder byteOrder,
            int flags,
            ListFW<RegionFW> regions,
            OctetsFW extension)
        {
            ChannelBuffer buffer;

            if (regions.isEmpty())
            {
                buffer = flags == 0 && extension.sizeof() != 0 ? NULL_BUFFER : null;
            }
            else
            {
                MutableInteger capacity = new MutableInteger();
                regions.forEach(r -> capacity.value += r.length());

                if (capacity.value == 0)
                {
                    buffer = ChannelBuffers.EMPTY_BUFFER;
                }
                else
                {
                    DirectBuffer view = new UnsafeBuffer(new byte[0]);
                    ByteBuffer byteBuf = allocateDirect(capacity.value).order(byteOrder);
                    regions.forEach(r ->
                    {
                        final int length = r.length();
                        final long resolved = resolveMemory.applyAsLong(r.address());
                        view.wrap(resolved, length);
                        view.getBytes(0, byteBuf, byteBuf.position(), length);
                        byteBuf.position(byteBuf.position() + length);
                    });
                    byteBuf.flip();
                    buffer = wrappedBuffer(byteBuf);
                }
            }

            return buffer;
        }
    }
}
