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
package org.reaktivity.k3po.nukleus.ext;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isA;
import static org.junit.rules.RuleChain.outerRule;

import java.nio.file.Paths;

import org.junit.ComparisonFailure;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runners.model.TestTimedOutException;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.reaktivity.k3po.nukleus.ext.rules.NukleusMemoryRule;

public class HalfDuplexIT
{
    private final K3poRule k3po = new K3poRule()
            .setScriptRoot("org/reaktivity/k3po/nukleus/ext/half.duplex");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final ExpectedException thrown = ExpectedException.none();

    private final NukleusMemoryRule memory = new NukleusMemoryRule().directory(Paths.get("target/nukleus-itests"))
                                                                    .minimumBlockSize(8 * 1024)
                                                                    .maximumBlockSize(1024 *1024);

    @Rule
    public final TestRule chain = outerRule(thrown).around(memory).around(k3po).around(timeout);

    @Test
    @Specification({
        "handshake/client",
        "handshake/server"
    })
    public void shouldHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.ext/client",
        "handshake.ext/server"
    })
    public void shouldHandshakeWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.authorized/client",
        "handshake.authorized/server"
    })
    public void shouldHandshakeWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.unequal.authorization/client",
        "handshake.unequal.authorization/server"
    })
    public void shouldFailHandshakeWithUnequalAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.transfer/client",
        "client.sent.transfer/server"
    })
    public void shouldReceiveClientSentTransfer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.transfer.fragmented/client",
        "client.sent.transfer.fragmented/server"
    })
    public void shouldReceiveClientSentTransferFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.transfer.ext/client",
        "client.sent.transfer.ext/server"
    })
    public void shouldReceiveClientSentTransferWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.transfer.missing.ext/client",
        "client.sent.transfer.missing.ext/server"
    })
    public void shouldRejectClientSentTransferMissingExtension() throws Exception
    {
        thrown.expect(hasProperty("failures", contains(asList(instanceOf(ComparisonFailure.class),
                                                              instanceOf(TestTimedOutException.class)))));
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.transfer/client",
        "server.sent.transfer/server"
    })
    public void shouldReceiveServerSentTransfer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.transfer.ext/client",
        "server.sent.transfer.ext/server"
    })
    public void shouldReceiveServerSentTransferWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.transfer.missing.ext/client",
        "server.sent.transfer.missing.ext/server"
    })
    public void shouldRejectServerSentTransferWithMissingExtension() throws Exception
    {
        thrown.expect(anyOf(isA(ComparisonFailure.class),
                            hasProperty("failures", hasItem(isA(ComparisonFailure.class)))));
        k3po.finish();
    }

    @Test
    @Specification({
        "client.write.close/client",
        "client.write.close/server"
    })
    public void shouldReceiveClientShutdownOutput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.write.close.ext/client",
        "client.write.close.ext/server"
    })
    public void shouldReceiveClientShutdownOutputWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.close/client",
        "client.close/server"
    })
    public void shouldReceiveClientClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.close/client",
        "server.close/server"
    })
    public void shouldReceiveServerClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.write.close/client",
        "server.write.close/server"
    })
    public void shouldReceiveServerShutdownOutput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.write.close.ext/client",
        "server.write.close.ext/server"
    })
    public void shouldReceiveServerShutdownOutputWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.write.abort/client",
        "server.sent.write.abort/server"
    })
    public void shouldReceiveServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.write.abort/client",
        "client.sent.write.abort/server"
    })
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("BEGIN vs RESET read order not yet guaranteed to match write order")
    @Test
    @Specification({
        "server.sent.read.and.write.abort/client",
        "server.sent.read.and.write.abort/server"
    })
    public void shouldReceiveServerSentReadAndWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("ABORT vs RESET read order not yet guaranteed to match write order")
    @Test
    @Specification({
        "client.sent.read.and.write.abort/client",
        "client.sent.read.and.write.abort/server"
    })
    public void shouldReceiveClientSentReadAndWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Ignore("BEGIN vs RESET read order not yet guaranteed to match write order")
    @Test
    @Specification({
        "server.sent.read.abort/client",
        "server.sent.read.abort/server"
    })
    public void shouldReceiveServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.read.abort/client",
        "client.sent.read.abort/server"
    })
    public void shouldReceiveClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.flush.empty.transfer.with.ext/client",
        "client.flush.empty.transfer.with.ext/server"
    })
    public void shouldReceiveClientFlushedEmptyTransferWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.flush.null.transfer.with.ext/client",
        "client.flush.null.transfer.with.ext/server"
    })
    public void shouldReceiveClientFlushedNullTransferWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.flush.empty.transfer.with.ext/client",
        "server.flush.empty.transfer.with.ext/server"
    })
    public void shouldReceiveServerFlushedEmptyTransferWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.flush.null.transfer.with.ext/client",
        "server.flush.null.transfer.with.ext/server"
    })
    public void shouldReceiveServerFlushedNullTransferWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.write.empty.transfer.with.ext/client",
        "server.write.empty.transfer.with.ext/server"
    })
    public void shouldReceiveServerWrittenEmptyTransferWithExtension() throws Exception
    {
        k3po.finish();
    }
}
