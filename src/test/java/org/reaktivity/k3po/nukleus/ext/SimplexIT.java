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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.isA;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class SimplexIT
{
    private final K3poRule k3po = new K3poRule()
            .setScriptRoot("org/reaktivity/k3po/nukleus/ext/simplex");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final ExpectedException thrown = ExpectedException.none();

    @Rule
    public final TestRule chain = outerRule(thrown).around(k3po).around(timeout);

    @Test
    @Specification({
        "correlated.handshake/client",
        "correlated.handshake/server"
    })
    public void shouldCorrelateHandshake() throws Exception
    {
        k3po.finish();
    }

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
        "client.sent.data/client",
        "client.sent.data/server"
    })
    public void shouldReceiveClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.data.ext/client",
        "client.sent.data.ext/server"
    })
    public void shouldReceiveClientSentDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.data.missing.ext/client",
        "client.sent.data.missing.ext/server"
    })
    public void shouldRejectClientSentDataMissingExtension() throws Exception
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
        "client.sent.write.abort/client",
        "client.sent.write.abort/server"
    })
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

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
        "server.sent.throttle/client",
        "server.sent.throttle/server"
    })
    public void shouldThrottleClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.throttle.message/client",
        "server.sent.throttle.message/server"
    })
    public void shouldThrottleClientSentDataPerMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.throttle.initial.only/client",
        "server.sent.throttle.initial.only/server"
    })
    public void shouldThrottleInitialOnlyClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.throttle.initial.only.update.none/client",
        "server.sent.throttle.initial.only.update.none/server"
    })
    public void shouldThrottleInitialOnlyClientSentDataUpdateNone() throws Exception
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
        "server.sent.overflow/client",
        "server.sent.overflow/server"
    })
    public void shouldOverflowClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.flush.data.ext/client",
        "client.flush.data.ext/server"
    })
    public void shouldReceiveClientFlushedEmptyDataWithExtension() throws Exception
    {
        k3po.finish();
    }
}
