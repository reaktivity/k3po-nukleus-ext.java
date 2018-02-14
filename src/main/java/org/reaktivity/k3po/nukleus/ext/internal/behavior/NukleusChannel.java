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

import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NullChannelBuffer.NULL_BUFFER;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.kaazing.k3po.driver.internal.netty.bootstrap.channel.AbstractChannel;
import org.kaazing.k3po.driver.internal.netty.channel.ChannelAddress;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.ListFW;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.types.stream.RegionFW;

public abstract class NukleusChannel extends AbstractChannel<NukleusChannelConfig>
{
    static final ChannelBufferFactory NATIVE_BUFFER_FACTORY = NukleusByteOrder.NATIVE.toBufferFactory();

    private final AtomicBoolean closing;

    private long sourceId;
    private long sourceAuth;
    private long targetId;
    private long targetAuth;

    final NukleusReaktor reaktor;
    final Deque<MessageEvent> writeRequests;

    long writeAddressBase;
    MutableDirectBuffer writeBuffer;

    private NukleusExtensionKind readExtKind;
    private ChannelBuffer readExtBuffer;

    private NukleusExtensionKind writeExtKind;
    private ChannelBuffer writeExtBuffer;

    private ChannelFuture beginOutputFuture;
    private ChannelFuture beginInputFuture;

    private long readerIndex;
    private long writerIndex;
    private int ackCount;

    private int writeCapacity;


    NukleusChannel(
        NukleusServerChannel parent,
        ChannelFactory factory,
        ChannelPipeline pipeline,
        ChannelSink sink,
        NukleusReaktor reaktor)
    {
        super(parent, factory, pipeline, sink, new DefaultNukleusChannelConfig());

        this.reaktor = reaktor;
        this.writeRequests = new LinkedList<>();
        this.targetId = ((long) getId()) | 0x8000000000000000L;

        this.writeCapacity = 64 * 1024;

        this.closing = new AtomicBoolean();
    }

    public void acquireWriteMemory()
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[0]);
        final long address = reaktor.acquire(writeCapacity);
        if (address == -1L)
        {
            throw new IllegalStateException("Unable to allocate memory block: " + writeCapacity);
        }
        final long resolvedAddress = reaktor.resolve(address);
        buffer.wrap(resolvedAddress, writeCapacity);
        this.writeAddressBase = address;
        this.writeBuffer = buffer;
    }

    public void releaseWriteMemory()
    {
        if (writeBuffer != null)
        {
            reaktor.release(writeAddressBase, writeCapacity);
        }
    }

    @Override
    public NukleusChannelAddress getLocalAddress()
    {
        return (NukleusChannelAddress) super.getLocalAddress();
    }

    @Override
    public NukleusChannelAddress getRemoteAddress()
    {
        return (NukleusChannelAddress) super.getRemoteAddress();
    }

    @Override
    protected void setBound()
    {
        super.setBound();
    }

    @Override
    protected void setConnected()
    {
        super.setConnected();
    }

    public boolean isClosing()
    {
        return closing.get();
    }

    public boolean setClosing()
    {
        return closing.compareAndSet(false, true);
    }

    @Override
    protected boolean isReadClosed()
    {
        return super.isReadClosed();
    }

    @Override
    protected boolean isWriteClosed()
    {
        return super.isWriteClosed();
    }

    @Override
    protected boolean setReadClosed()
    {
        return super.setReadClosed();
    }

    @Override
    protected boolean setWriteClosed()
    {
        releaseWriteMemory();
        return super.setWriteClosed();
    }

    @Override
    protected boolean setReadAborted()
    {
        return super.setReadAborted();
    }

    @Override
    protected boolean setWriteAborted()
    {
        releaseWriteMemory();
        return super.setWriteAborted();
    }

    @Override
    protected boolean setClosed()
    {
        releaseWriteMemory();
        return super.setClosed();
    }

    @Override
    protected void setRemoteAddress(ChannelAddress remoteAddress)
    {
        super.setRemoteAddress(remoteAddress);
    }

    @Override
    protected void setLocalAddress(ChannelAddress localAddress)
    {
        super.setLocalAddress(localAddress);
    }

    @Override
    public String toString()
    {
        ChannelAddress localAddress = this.getLocalAddress();
        String description = localAddress != null ? localAddress.toString() : super.toString();
        return String.format("%s [sourceId=%d, targetId=%d]", description, sourceId, targetId);
    }

    public void sourceId(
        long sourceId)
    {
        this.sourceId = sourceId;
    }

    public long sourceId()
    {
        return sourceId;
    }

    public long targetId()
    {
        return targetId;
    }

    public void sourceAuth(
        long sourceAuth)
    {
        this.sourceAuth = sourceAuth;
    }

    public long sourceAuth()
    {
        return sourceAuth;
    }

    public void targetAuth(
        long targetAuth)
    {
        this.targetAuth = targetAuth;
    }

    public long targetAuth()
    {
        return targetAuth;
    }

    public ChannelFuture beginOutputFuture()
    {
        if (beginOutputFuture == null)
        {
            beginOutputFuture = Channels.future(this);
        }

        return beginOutputFuture;
    }

    public ChannelFuture beginInputFuture()
    {
        if (beginInputFuture == null)
        {
            beginInputFuture = Channels.future(this);
        }

        return beginInputFuture;
    }

    public ChannelBuffer writeExtBuffer(
        NukleusExtensionKind writeExtKind,
        boolean readonly)
    {
        if (this.writeExtKind != writeExtKind)
        {
            if (readonly)
            {
                return ChannelBuffers.EMPTY_BUFFER;
            }
            else
            {
                if (writeExtBuffer == null)
                {
                    writeExtBuffer = getConfig().getBufferFactory().getBuffer(8192);
                }
                else
                {
                    writeExtBuffer.clear();
                }
                this.writeExtKind = writeExtKind;
            }
        }

        return writeExtBuffer;
    }

    public ChannelBuffer readExtBuffer(
        NukleusExtensionKind readExtKind)
    {
        if (this.readExtKind != readExtKind)
        {
            if (readExtBuffer == null)
            {
                readExtBuffer = getConfig().getBufferFactory().getBuffer(8192);
            }
            else
            {
                readExtBuffer.clear();
            }
            this.readExtKind = readExtKind;
        }

        return readExtBuffer;
    }

    public void acknowledge(
        ListFW<RegionFW> regions)
    {
        ackCount++;

        regions.forEach(this::acknowledge);
    }

    public boolean hasAcknowledged()
    {
        return ackCount != 0;
    }

    public int writableBytes()
    {
        return writeBuffer.capacity() - (int)(writerIndex - readerIndex);
    }

    public void flushBytes(
        ListFW.Builder<RegionFW.Builder, RegionFW> regions,
        ChannelBuffer writeBuf,
        int writableBytes,
        long streamId)
    {
        if (writeBuf != NULL_BUFFER)
        {
            final long address = flushBytes(writeBuf, writableBytes);
            regions.item(r -> r.address(address).length(writableBytes).streamId(streamId));
        }
    }

    public long flushBytes(
        ChannelBuffer writeBuf,
        int writableBytes)
    {
        final ByteBuffer byteBuffer = writeBuf.toByteBuffer();
        final int writerAt = (int) (writerIndex & (writeBuffer.capacity() - 1));
        final int writerLimit = writerAt + writableBytes;
        final long writeAddress = writeAddressBase + writerAt;
        if (writerLimit <= writeBuffer.capacity())
        {
            writeBuffer.putBytes(writerAt, byteBuffer, writableBytes);
            writerIndex += writableBytes;
        }
        else
        {
            int writableBytes0 = writeBuffer.capacity() - writerAt;
            int writableBytes1 = writerLimit - writeBuffer.capacity();

            writeBuffer.putBytes(writerAt, byteBuffer, writableBytes0);
            writerIndex = 0;
            writeBuffer.putBytes(0, byteBuffer, writableBytes1);
            writerIndex = writableBytes1;
        }

        writeBuf.skipBytes(writableBytes);

        return writeAddress;
    }

    private void acknowledge(
        RegionFW region)
    {
        // TODO: verify address is contiguous
        readerIndex += region.length();

        if (readerIndex == writerIndex)
        {
            readerIndex = writerIndex = 0L;
        }
    }
}
