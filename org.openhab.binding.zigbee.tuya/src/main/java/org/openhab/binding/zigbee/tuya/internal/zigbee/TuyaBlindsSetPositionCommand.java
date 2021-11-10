package org.openhab.binding.zigbee.tuya.internal.zigbee;

import com.zsmartsystems.zigbee.zcl.ZclFieldDeserializer;
import com.zsmartsystems.zigbee.zcl.ZclFieldSerializer;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class TuyaBlindsSetPositionCommand extends ZclTuyaCommand {
    private final int position;

    public TuyaBlindsSetPositionCommand(int position) {
        this.position = position;
    }

    @Override
    public void serialize(final ZclFieldSerializer serializer) {
        super.serialize(serializer);

        serializer.serialize(0x02, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x02, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x00, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x04, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x00, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x00, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x00, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(position, ZclDataType.UNSIGNED_8_BIT_INTEGER);
    }

    @Override
    public void deserialize(final ZclFieldDeserializer deserializer) {
        // Not expected to get here
    }

    @Override
    public String toString() {
        return "TuyaBlindsSetPositionCommand [" + super.toString() + ", position=" + position + "]";
    }

}
