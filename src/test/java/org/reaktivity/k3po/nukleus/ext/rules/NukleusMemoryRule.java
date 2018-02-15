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
package org.reaktivity.k3po.nukleus.ext.rules;

import java.nio.file.Path;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.layout.MemoryLayout;

public final class NukleusMemoryRule implements TestRule
{
    private Path directory;
    private Integer minimumBlockSize;
    private Integer maximumBlockSize;

    public NukleusMemoryRule directory(
        Path directory)
    {
        this.directory = directory;
        return this;
    }

    public NukleusMemoryRule minimumBlockSize(
        int minimumBlockSize)
    {
        this.minimumBlockSize = minimumBlockSize;
        return this;
    }

    public NukleusMemoryRule maximumBlockSize(
        int maximumBlockSize)
    {
        this.maximumBlockSize = maximumBlockSize;
        return this;
    }

    @Override
    public Statement apply(Statement base, Description description)
    {
        if (directory == null)
        {
            throw new IllegalStateException("directory not set");
        }

        if (minimumBlockSize == null)
        {
            throw new IllegalStateException("minimum block size not set");
        }

        if (maximumBlockSize == null)
        {
            throw new IllegalStateException("maximum block size not set");
        }

        return new NukleusMemoryStatement(base);
    }

    private final class NukleusMemoryStatement extends Statement
    {
        private final Statement base;

        private NukleusMemoryStatement(
            Statement base)
        {
            this.base = base;
        }

        @Override
        public void evaluate() throws Throwable
        {
            try (MemoryLayout memoryLayout = new MemoryLayout.Builder()
                                                             .path(directory.resolve("memory0"))
                                                             .minimumBlockSize(minimumBlockSize)
                                                             .maximumBlockSize(maximumBlockSize)
                                                             .create(true)
                                                             .build())
            {
                base.evaluate();
            }
        }
    }
}
