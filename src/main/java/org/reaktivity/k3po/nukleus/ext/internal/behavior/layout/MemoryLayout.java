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
package org.reaktivity.k3po.nukleus.ext.internal.behavior.layout;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.IoUtil.createEmptyFile;
import static org.agrona.IoUtil.mapExistingFile;
import static org.agrona.IoUtil.unmap;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.memory.DefaultMemoryManager;

public final class MemoryLayout extends Layout
{

    private final UnsafeBuffer memoryBuffer;
    private final int minimumBlockSize;
    private final int maximumBlockSize;
    private final int capacity;

    private MemoryLayout(
        UnsafeBuffer memoryBuffer,
        int minimumBlockSize,
        int maximumBlockSize,
        int capacity)
    {
        this.memoryBuffer = memoryBuffer;
        this.minimumBlockSize = minimumBlockSize;
        this.maximumBlockSize = maximumBlockSize;
        this.capacity = capacity;
    }

    @Override
    public void close()
    {
        unmap(memoryBuffer().byteBuffer());
    }

    public MutableDirectBuffer memoryBuffer()
    {
        return memoryBuffer;
    }

    public int minimumBlockSize()
    {
        return minimumBlockSize;
    }

    public int capacity()
    {
        return capacity;
    }

    public int maximumBlockSize()
    {
        return maximumBlockSize;
    }

    public static final class Builder extends Layout.Builder<MemoryLayout>
    {
        private Path path;
        private int capacity;
        private int minimumBlockSize;
        private boolean readonly;

        public Builder path(Path path)
        {
            this.path = path;
            return this;
        }

        public Builder capacity(
            int capacity)
        {
            this.capacity = capacity;
            return this;
        }

        public Builder readonly(
            boolean readonly)
        {
            this.readonly = readonly;
            return this;
        }

        public Builder minimumBlockSize(
            int minimumBlockSize)
        {
            this.minimumBlockSize = minimumBlockSize;
            return this;
        }

        @Override
        public MemoryLayout build()
        {
            // TODO: when not creating file, all aspects of metadata should be observed from file contents
            //       which simplifies keeping the metadata configuration in sync
            final File memory = path.toFile();

            long sizeToAllocate = sizeToAllocate(capacity, capacity, minimumBlockSize);
            if (!readonly)
            {
                CloseHelper.close(createEmptyFile(memory, sizeToAllocate));
            }
            final MappedByteBuffer mappedMemory = mapExistingFile(memory, "memory", 0, sizeToAllocate);

            final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(mappedMemory);

            return new MemoryLayout(unsafeBuffer, minimumBlockSize, capacity, capacity);
        }

        private static long sizeToAllocate(
            int capacity,
            int largestBlockSize,
            int smallestBlockSize)
        {
            int requiredSize = capacity + DefaultMemoryManager.sizeOfMetaData(
                    capacity,
                    largestBlockSize,
                    smallestBlockSize);
            return BitUtil.align(requiredSize, SIZE_OF_LONG);
        }

    }

}
