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

import java.nio.file.Path;

import org.reaktivity.k3po.nukleus.ext.internal.behavior.layout.MemoryLayout;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.memory.DefaultMemoryManager;
import org.reaktivity.nukleus.buffer.MemoryManager;

public final class NukleusMemory implements MemoryManager, AutoCloseable
{
    private final MemoryLayout layout;
    private final MemoryManager manager;

    public NukleusMemory(
        Path directory)
    {
        this.layout = new MemoryLayout.Builder()
                                      .path(directory.resolve("memory0"))
                                      .build();
        this.manager = new DefaultMemoryManager(layout);
    }

    @Override
    public long resolve(
        long address)
    {
        return manager.resolve(address);
    }

    @Override
    public long acquire(int capacity)
    {
        return manager.acquire(capacity);
    }

    @Override
    public void release(long address, int capacity)
    {
        manager.release(address, capacity);
    }

    @Override
    public void close() throws Exception
    {
        layout.close();
    }
}
