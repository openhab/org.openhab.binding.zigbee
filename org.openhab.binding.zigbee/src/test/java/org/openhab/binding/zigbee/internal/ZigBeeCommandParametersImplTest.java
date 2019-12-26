package org.openhab.binding.zigbee.internal;

import org.junit.Test;
import org.openhab.binding.zigbee.ZigBeeCommandParameter;

import java.util.Optional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ZigBeeCommandParametersImplTest {
    @Test
    public void testCommandParameters() {
        ZigBeeCommandParametersImpl.UsageTracker tracker = new ZigBeeCommandParametersImpl.UsageTracker(new ZigBeeCommandParametersImpl());

        assertThat(tracker.setParameters(), empty());
        assertThat(tracker.unusedParams(), empty());

        tracker.add(ZigBeeCommandParameter.TRANSITION_TIME, 15);

        assertThat(tracker.unusedParams(), contains(ZigBeeCommandParameter.TRANSITION_TIME));
        assertThat(tracker.setParameters(), contains(ZigBeeCommandParameter.TRANSITION_TIME));

        assertEquals(tracker.get(ZigBeeCommandParameter.TRANSITION_TIME), Optional.of(15));
        assertThat(tracker.unusedParams(), empty());
    }
}