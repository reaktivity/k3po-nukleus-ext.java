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

import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusChannel.NATIVE_BUFFER_FACTORY;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusTransmission.SIMPLEX;
import static org.reaktivity.k3po.nukleus.ext.internal.types.NukleusTypeSystem.OPTION_BYTE_ORDER;
import static org.reaktivity.k3po.nukleus.ext.internal.util.Conversions.convertToLong;

import java.util.Objects;

import org.kaazing.k3po.driver.internal.netty.bootstrap.channel.DefaultChannelConfig;

public class DefaultNukleusChannelConfig extends DefaultChannelConfig implements NukleusChannelConfig
{
    private long correlation;
    private String readPartition;
    private String writePartition;
    private NukleusTransmission transmission = SIMPLEX;

    public DefaultNukleusChannelConfig()
    {
        super();
        setBufferFactory(NATIVE_BUFFER_FACTORY);
    }

    @Override
    public void setCorrelation(
        long correlation)
    {
        this.correlation = correlation;
    }

    @Override
    public long getCorrelation()
    {
        return correlation;
    }

    @Override
    public void setReadPartition(
        String partition)
    {
        this.readPartition = partition;
    }

    @Override
    public String getReadPartition()
    {
        return readPartition;
    }

    @Override
    public void setWritePartition(
        String writePartition)
    {
        this.writePartition = writePartition;
    }

    @Override
    public String getWritePartition()
    {
        return writePartition;
    }

    @Override
    public void setTransmission(
        NukleusTransmission transmission)
    {
        this.transmission = transmission;
    }

    @Override
    public NukleusTransmission getTransmission()
    {
        return transmission;
    }

    @Override
    protected boolean setOption0(
        String key,
        Object value)
    {
        if (super.setOption0(key, value))
        {
            return true;
        }
        else if ("correlation".equals(key))
        {
            setCorrelation(convertToLong(value));
        }
        else if ("readPartition".equals(key))
        {
            setReadPartition(Objects.toString(value, null));
        }
        else if ("writePartition".equals(key))
        {
            setWritePartition(Objects.toString(value, null));
        }
        else if ("transmission".equals(key))
        {
            setTransmission(NukleusTransmission.decode(Objects.toString(value, null)));
        }
        else if (OPTION_BYTE_ORDER.getName().equals(key))
        {
            setBufferFactory(NukleusByteOrder.decode(Objects.toString(value, "native")).toBufferFactory());
        }
        else
        {
            return false;
        }

        return true;
    }
}
