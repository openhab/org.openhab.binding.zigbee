package org.openhab.binding.zigbee.tuya.internal.zigbee;

import com.zsmartsystems.zigbee.zcl.ZclCommand;
import com.zsmartsystems.zigbee.zcl.ZclFieldDeserializer;
import com.zsmartsystems.zigbee.zcl.ZclFieldSerializer;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 * Abstract base command class for all commands in the <b>Tuya</b> cluster (<i>Cluster ID 0xEF00</i>).
 * All commands sent through the {@link ZclTuyaCluster} must extend this class.
 *
 * @author Chris Jackson - Initial Contribution
 */
public abstract class ZclTuyaCommand extends ZclCommand {
    private int sequence;
    protected int[] data;

    protected void setSequenceCounter(int sequence) {
        this.sequence = sequence;
    }

    @Override
    public void serialize(final ZclFieldSerializer serializer) {
        serializer.serialize(0x00, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(sequence, ZclDataType.UNSIGNED_8_BIT_INTEGER);

    }

    @Override
    public void deserialize(final ZclFieldDeserializer deserializer) {
        deserializer.deserialize(ZclDataType.UNSIGNED_8_BIT_INTEGER);
        sequence = (Integer) deserializer.deserialize(ZclDataType.UNSIGNED_8_BIT_INTEGER);
    }

    @Override
    public String toString() {
        return String.format("sequence=%02X", sequence);
    }
}
