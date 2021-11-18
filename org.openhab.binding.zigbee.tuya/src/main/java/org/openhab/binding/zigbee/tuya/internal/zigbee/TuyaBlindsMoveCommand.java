package org.openhab.binding.zigbee.tuya.internal.zigbee;

import com.zsmartsystems.zigbee.zcl.ZclFieldDeserializer;
import com.zsmartsystems.zigbee.zcl.ZclFieldSerializer;
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType;

/**
 *
 * @author Chris Jackson - Initial Contribution
 *
 */
public class TuyaBlindsMoveCommand extends ZclTuyaCommand {
    public enum Direction {
        UP,
        DOWN,
        STOP
    }

    private static int DIRECTION_UP = 0;
    private static int DIRECTION_DOWN = 2;
    private static int DIRECTION_STOP = 1;

    private final Direction direction;

    public TuyaBlindsMoveCommand(Direction direction) {
        this.direction = direction;
    }

    @Override
    public void serialize(final ZclFieldSerializer serializer) {
        super.serialize(serializer);

        serializer.serialize(0x01, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x04, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x00, ZclDataType.UNSIGNED_8_BIT_INTEGER);
        serializer.serialize(0x01, ZclDataType.UNSIGNED_8_BIT_INTEGER);

        switch (direction) {
            case DOWN:
                serializer.serialize(DIRECTION_DOWN, ZclDataType.UNSIGNED_8_BIT_INTEGER);
                break;
            case UP:
                serializer.serialize(DIRECTION_UP, ZclDataType.UNSIGNED_8_BIT_INTEGER);
                break;
            case STOP:
            default:
                serializer.serialize(DIRECTION_STOP, ZclDataType.UNSIGNED_8_BIT_INTEGER);
                break;
        }

    }

    @Override
    public void deserialize(final ZclFieldDeserializer deserializer) {
        // Not expected to get here
    }

    @Override
    public String toString() {
        return "TuyaBlindsMoveCommand [" + super.toString() + ", direction=" + direction + "]";
    }

}
