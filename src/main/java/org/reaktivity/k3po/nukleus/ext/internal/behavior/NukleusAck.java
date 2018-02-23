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

import java.util.LinkedList;
import java.util.List;

public class NukleusAck
{
    final long streamId;
    final int flags;
    final List<NukleusRegion> regions;

    public NukleusAck(
        long streamId,
        int flags)
    {
        this.streamId = streamId;
        this.flags = flags;
        this.regions = new LinkedList<>();
    }

    public void addRegion(
        long address,
        int length,
        long streamId)
    {
        regions.add(new NukleusRegion(address, length, streamId));
    }

    @Override
    public String toString()
    {
        return String.format("ACK [streamId=0x%016x, flags=%d, regions=%s", streamId, flags, regions);
    }

    public static final class NukleusRegion
    {
        final long address;
        final int length;
        final long streamId;

        public NukleusRegion(
            long address,
            int length,
            long streamId)
        {
            this.address = address;
            this.length = length;
            this.streamId = streamId;
        }

        @Override
        public String toString()
        {
            return String.format("REGION [address=%d, length=%d, streamId=0x%016x", address, length, streamId);
        }
    }
}
