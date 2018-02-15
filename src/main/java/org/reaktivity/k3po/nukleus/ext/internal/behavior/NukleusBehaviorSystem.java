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

import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toList;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.BEGIN;
import static org.reaktivity.k3po.nukleus.ext.internal.behavior.NukleusExtensionKind.TRANSFER;
import static org.reaktivity.k3po.nukleus.ext.internal.types.NukleusTypeSystem.CONFIG_BEGIN_EXT;
import static org.reaktivity.k3po.nukleus.ext.internal.types.NukleusTypeSystem.CONFIG_TRANSFER_EMPTY;
import static org.reaktivity.k3po.nukleus.ext.internal.types.NukleusTypeSystem.CONFIG_TRANSFER_EXT;
import static org.reaktivity.k3po.nukleus.ext.internal.types.NukleusTypeSystem.CONFIG_TRANSFER_NULL;
import static org.reaktivity.k3po.nukleus.ext.internal.types.NukleusTypeSystem.OPTION_PARTITION;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.netty.channel.ChannelHandler;
import org.kaazing.k3po.driver.internal.behavior.BehaviorSystemSpi;
import org.kaazing.k3po.driver.internal.behavior.ReadConfigFactory;
import org.kaazing.k3po.driver.internal.behavior.ReadOptionFactory;
import org.kaazing.k3po.driver.internal.behavior.WriteConfigFactory;
import org.kaazing.k3po.driver.internal.behavior.WriteOptionFactory;
import org.kaazing.k3po.driver.internal.behavior.handler.codec.MessageDecoder;
import org.kaazing.k3po.driver.internal.behavior.handler.codec.MessageEncoder;
import org.kaazing.k3po.driver.internal.behavior.handler.command.WriteConfigHandler;
import org.kaazing.k3po.lang.internal.RegionInfo;
import org.kaazing.k3po.lang.internal.ast.AstReadConfigNode;
import org.kaazing.k3po.lang.internal.ast.AstReadOptionNode;
import org.kaazing.k3po.lang.internal.ast.AstWriteConfigNode;
import org.kaazing.k3po.lang.internal.ast.AstWriteOptionNode;
import org.kaazing.k3po.lang.internal.ast.matcher.AstValueMatcher;
import org.kaazing.k3po.lang.internal.ast.value.AstValue;
import org.kaazing.k3po.lang.types.StructuredTypeInfo;
import org.kaazing.k3po.lang.types.TypeInfo;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.config.NukleusExtensionDecoder;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.config.NukleusExtensionEncoder;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.config.ReadBeginExtHandler;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.config.ReadTransferExtHandler;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.config.ReadTransferNullHandler;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.config.WriteTransferEmptyHandler;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.option.ReadPartitionHandler;
import org.reaktivity.k3po.nukleus.ext.internal.behavior.option.WritePartitionHandler;

public class NukleusBehaviorSystem implements BehaviorSystemSpi
{
    private final Map<TypeInfo<?>, ReadOptionFactory> readOptionFactories;
    private final Map<TypeInfo<?>, WriteOptionFactory> writeOptionFactories;

    private final Map<StructuredTypeInfo, ReadConfigFactory> readConfigFactories;
    private final Map<StructuredTypeInfo, WriteConfigFactory> writeConfigFactories;

    public NukleusBehaviorSystem()
    {
        this.readOptionFactories = singletonMap(OPTION_PARTITION, NukleusBehaviorSystem::newReadPartitionHandler);
        this.writeOptionFactories = singletonMap(OPTION_PARTITION, NukleusBehaviorSystem::newWritePartitionHandler);

        Map<StructuredTypeInfo, ReadConfigFactory> readConfigFactories = new LinkedHashMap<>();
        readConfigFactories.put(CONFIG_BEGIN_EXT, NukleusBehaviorSystem::newReadBeginExtHandler);
        readConfigFactories.put(CONFIG_TRANSFER_EXT, NukleusBehaviorSystem::newReadTransferExtHandler);
        readConfigFactories.put(CONFIG_TRANSFER_NULL, NukleusBehaviorSystem::newReadTransferNullHandler);
        this.readConfigFactories = unmodifiableMap(readConfigFactories);

        Map<StructuredTypeInfo, WriteConfigFactory> writeConfigFactories = new LinkedHashMap<>();
        writeConfigFactories.put(CONFIG_BEGIN_EXT, NukleusBehaviorSystem::newWriteBeginExtHandler);
        writeConfigFactories.put(CONFIG_TRANSFER_EMPTY, NukleusBehaviorSystem::newWriteTransferEmptyHandler);
        writeConfigFactories.put(CONFIG_TRANSFER_EXT, NukleusBehaviorSystem::newWriteTransferExtHandler);
        this.writeConfigFactories = unmodifiableMap(writeConfigFactories);
    }

    @Override
    public Set<StructuredTypeInfo> getReadConfigTypes()
    {
        return readConfigFactories.keySet();
    }

    @Override
    public Set<StructuredTypeInfo> getWriteConfigTypes()
    {
        return writeConfigFactories.keySet();
    }

    @Override
    public ReadConfigFactory readConfigFactory(
        StructuredTypeInfo configType)
    {
        return readConfigFactories.get(configType);
    }

    @Override
    public WriteConfigFactory writeConfigFactory(
        StructuredTypeInfo configType)
    {
        return writeConfigFactories.get(configType);
    }

    @Override
    public Set<TypeInfo<?>> getReadOptionTypes()
    {
        return readOptionFactories.keySet();
    }

    @Override
    public Set<TypeInfo<?>> getWriteOptionTypes()
    {
        return writeOptionFactories.keySet();
    }

    @Override
    public ReadOptionFactory readOptionFactory(
        TypeInfo<?> optionType)
    {
        return readOptionFactories.get(optionType);
    }

    @Override
    public WriteOptionFactory writeOptionFactory(
        TypeInfo<?> optionType)
    {
        return writeOptionFactories.get(optionType);
    }

    @SuppressWarnings("unchecked")
    private static ReadPartitionHandler newReadPartitionHandler(
        AstReadOptionNode node)
    {
        Supplier<String> partition = ((AstValue<String>) node.getOptionValue())::getValue;

        ReadPartitionHandler handler = new ReadPartitionHandler(partition);
        handler.setRegionInfo(node.getRegionInfo());
        return handler;
    }

    @SuppressWarnings("unchecked")
    private static WritePartitionHandler newWritePartitionHandler(
        AstWriteOptionNode node)
    {
        Supplier<String> partition = ((AstValue<String>) node.getOptionValue())::getValue;

        WritePartitionHandler handler = new WritePartitionHandler(partition);
        handler.setRegionInfo(node.getRegionInfo());
        return handler;
    }

    private static ReadBeginExtHandler newReadBeginExtHandler(
        AstReadConfigNode node,
        Function<AstValueMatcher, MessageDecoder> decoderFactory)
    {
        RegionInfo regionInfo = node.getRegionInfo();
        StructuredTypeInfo type = node.getType();
        List<MessageDecoder> decoders = node.getMatchers().stream().map(decoderFactory).collect(toList());

        ReadBeginExtHandler handler = new ReadBeginExtHandler(new NukleusExtensionDecoder(BEGIN, type, decoders));
        handler.setRegionInfo(regionInfo);
        return handler;
    }

    private static ReadTransferExtHandler newReadTransferExtHandler(
        AstReadConfigNode node,
        Function<AstValueMatcher, MessageDecoder> decoderFactory)
    {
        RegionInfo regionInfo = node.getRegionInfo();
        StructuredTypeInfo type = node.getType();
        List<MessageDecoder> decoders = node.getMatchers().stream().map(decoderFactory).collect(toList());

        ReadTransferExtHandler handler = new ReadTransferExtHandler(new NukleusExtensionDecoder(TRANSFER, type, decoders));
        handler.setRegionInfo(regionInfo);
        return handler;
    }

    private static ChannelHandler newReadTransferNullHandler(
        AstReadConfigNode node,
        Function<AstValueMatcher, MessageDecoder> decoderFactory)
    {
        RegionInfo regionInfo = node.getRegionInfo();
        ReadTransferNullHandler handler = new ReadTransferNullHandler();
        handler.setRegionInfo(regionInfo);
        return handler;
    }

    private static WriteConfigHandler newWriteBeginExtHandler(
        AstWriteConfigNode node,
        Function<AstValue<?>, MessageEncoder> encoderFactory)
    {
        StructuredTypeInfo type = node.getType();
        List<MessageEncoder> encoders = node.getValues().stream().map(encoderFactory).collect(toList());

        WriteConfigHandler handler = new WriteConfigHandler(new NukleusExtensionEncoder(BEGIN, type, encoders));
        handler.setRegionInfo(node.getRegionInfo());
        return handler;
    }

    private static WriteConfigHandler newWriteTransferExtHandler(
        AstWriteConfigNode node,
        Function<AstValue<?>, MessageEncoder> encoderFactory)
    {
        StructuredTypeInfo type = node.getType();
        List<MessageEncoder> encoders = node.getValues().stream().map(encoderFactory).collect(toList());

        WriteConfigHandler handler = new WriteConfigHandler(new NukleusExtensionEncoder(TRANSFER, type, encoders));
        handler.setRegionInfo(node.getRegionInfo());
        return handler;
    }

    private static WriteTransferEmptyHandler newWriteTransferEmptyHandler(
        AstWriteConfigNode node,
        Function<AstValue<?>, MessageEncoder> encoderFactory)
    {
        WriteTransferEmptyHandler handler = new WriteTransferEmptyHandler();
        handler.setRegionInfo(node.getRegionInfo());
        return handler;
    }
}
