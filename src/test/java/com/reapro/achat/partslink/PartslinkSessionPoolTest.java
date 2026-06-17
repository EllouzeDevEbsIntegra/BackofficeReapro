package com.reapro.achat.partslink;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests d'isolation / concurrence du pool de sessions navigateur, SANS Chrome réel
 * (factory factice + sessions mockées).
 */
class PartslinkSessionPoolTest {

    private PartslinkProperties props(int maxSessions, int acquireTimeoutSeconds) {
        PartslinkProperties p = new PartslinkProperties();
        p.setEnabled(true);
        p.getPool().setEnabled(true);
        p.getPool().setMaxSessions(maxSessions);
        p.getPool().setAcquireTimeoutSeconds(acquireTimeoutSeconds);
        p.getPool().setIdleTimeoutMinutes(30);
        return p;
    }

    private PartslinkBrowserSession mockSession(boolean alive) {
        PartslinkBrowserSession s = mock(PartslinkBrowserSession.class);
        when(s.isAlive()).thenReturn(alive);
        when(s.getCreatedAt()).thenReturn(Instant.now());
        when(s.getLastActivityAt()).thenReturn(Instant.now());
        return s;
    }

    @Test
    void leaseReturnsSession_andTracksBusyCount() {
        PartslinkBrowserSession s = mockSession(true);
        PartslinkBrowserSessionFactory factory = mock(PartslinkBrowserSessionFactory.class);
        when(factory.create(anyInt())).thenReturn(s);

        PartslinkSessionPool pool = new PartslinkSessionPool(props(1, 1), factory);

        PartslinkBrowserSession leased = pool.lease();
        assertThat(leased).isSameAs(s);
        assertThat(pool.getStatus().busy()).isEqualTo(1);
        assertThat(pool.getStatus().idle()).isEqualTo(0);

        pool.release(leased);
        assertThat(pool.getStatus().busy()).isEqualTo(0);
        assertThat(pool.getStatus().idle()).isEqualTo(1);
    }

    @Test
    void whenAllSlotsBusy_nextLeaseTimesOutWithBusyException() {
        PartslinkBrowserSession s = mockSession(true);
        PartslinkBrowserSessionFactory factory = mock(PartslinkBrowserSessionFactory.class);
        when(factory.create(anyInt())).thenReturn(s);

        PartslinkSessionPool pool = new PartslinkSessionPool(props(1, 1), factory);

        PartslinkBrowserSession held = pool.lease(); // occupe l'unique slot

        long start = System.currentTimeMillis();
        assertThatThrownBy(pool::lease).isInstanceOf(PartslinkPoolBusyException.class);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isGreaterThanOrEqualTo(900); // a bien attendu le timeout (~1s)

        pool.release(held);
        // une fois libéré, on peut de nouveau emprunter
        assertThat(pool.lease()).isSameAs(s);
    }

    @Test
    void releasedSessionIsReused_noNewBrowserCreated() {
        PartslinkBrowserSession s = mockSession(true);
        PartslinkBrowserSessionFactory factory = mock(PartslinkBrowserSessionFactory.class);
        when(factory.create(anyInt())).thenReturn(s);

        PartslinkSessionPool pool = new PartslinkSessionPool(props(2, 1), factory);

        pool.release(pool.lease());
        pool.release(pool.lease());

        verify(factory, times(1)).create(anyInt()); // réutilisation, pas de 2e Chrome
    }

    @Test
    void deadSessionOnLease_isDiscardedAndRecreated() {
        PartslinkBrowserSession dead = mockSession(false);
        PartslinkBrowserSession alive = mockSession(true);
        PartslinkBrowserSessionFactory factory = mock(PartslinkBrowserSessionFactory.class);
        when(factory.create(anyInt())).thenReturn(dead, alive);

        PartslinkSessionPool pool = new PartslinkSessionPool(props(1, 2), factory);

        PartslinkBrowserSession leased = pool.lease();
        assertThat(leased).isSameAs(alive);
        verify(factory, times(2)).create(anyInt());
        verify(dead).close(); // le slot mort est fermé proprement (slot libéré)
    }

    @Test
    void disabledPool_refusesLease() {
        PartslinkProperties p = props(5, 1);
        p.getPool().setEnabled(false);
        PartslinkBrowserSessionFactory factory = mock(PartslinkBrowserSessionFactory.class);
        PartslinkSessionPool pool = new PartslinkSessionPool(p, factory);

        assertThatThrownBy(pool::lease).isInstanceOf(IllegalStateException.class);
    }
}
