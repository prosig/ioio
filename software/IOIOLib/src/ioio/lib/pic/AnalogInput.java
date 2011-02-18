package ioio.lib.pic;

import android.util.Log;

import ioio.lib.IOIOException.ConnectionLostException;
import ioio.lib.IOIOException.InvalidStateException;
import ioio.lib.Input;

import java.io.IOException;
import java.io.InputStream;

/**
 * Represent and manage analog input pins on the IOIO.
 *
 * @author arshan
 */
public class AnalogInput extends IOIOPin implements IOIOPacketListener, Input<Float> {

	IOIOImpl ioio;
	int value = 0;

	int counter = 0;

	boolean active = false;
	private int reportPin = 0;

	public AnalogInput(IOIOImpl ioio, int pin, PacketFramerRegistry framerRegistry) throws ConnectionLostException {
		super(pin);
		this.ioio = ioio;
		// note: the first analog pin that gets registered with register it's framer
		// all other pins will call this unsuccessfully. The first registered framer
		// should handle this correctly for any analog pin thereafter
        framerRegistry.registerFramer(Constants.REPORT_ANALOG_FORMAT, ANALOG_IN_PACKET_FRAMER);
        framerRegistry.registerFramer(Constants.REPORT_ANALOG_STATUS, ANALOG_IN_PACKET_FRAMER);
        framerRegistry.registerFramer(Constants.SET_ANALOG_INPUT, ANALOG_IN_PACKET_FRAMER);
		ioio.registerListener(this);
        init();
	}

	private void init() throws ConnectionLostException {
		ioio.queuePacket(new IOIOPacket(
				Constants.SET_ANALOG_INPUT,
				new byte[]{(byte)pin}
		));
	}

	// TODO(TF): decide on units, mV? let the user set them?
	@Override
    public Float read() throws InvalidStateException {
	    if (isInvalid()) {
	        throw Constants.INVALID_STATE_EXCEPTION;
	    }
		return value / 1023.0f;
	}

	@Override
    public void handlePacket(IOIOPacket packet) {
		switch (packet.message){
		case Constants.SET_ANALOG_INPUT:
			if (packet.payload[0] == pin) {
				active = true;
			}
			break;

		case Constants.REPORT_ANALOG_FORMAT:
            Log.i("IOIO-ANALOG", "analog format packet: " + packet.toString());
			// Record where in the paylod my pin number is for future use.
			for (int x = 1; x < packet.payload.length; x++) {
				if (packet.payload[x] == pin) {
					reportPin = x-1;
					break;
				}
			}
			break;

		case Constants.REPORT_ANALOG_STATUS:
            if (packet.payload == null || packet.payload.length == 0) {
                Log.d("IOIO-ANALOG", "payload is strange");
                return;
            }
			// TODO(arshan): make these class vars.
			int offset = (reportPin / 4) * 5;
			int rem  = reportPin % 4;

			// MSB
			value = (packet.payload[offset + rem + 1]) << 2;
			// LSB
			value |= (packet.payload[offset] & (0x3 << (rem*2))) >> (rem * 2);
	        value &= 0x3ff;
			break;
		}
	}

    @Override
    public void close() {
        // TODO(TF): Implement this
    }

    private final PacketFramer ANALOG_IN_PACKET_FRAMER = new PacketFramer() {
        int analogPinCount = 0;
        private int analogPinBytes = 0;
        @Override
        public IOIOPacket frame(byte message, InputStream in) throws IOException {
            switch (message) {
                case Constants.REPORT_ANALOG_FORMAT:
                    analogPinCount = Bytes.readByte(in);
                    int groups = (analogPinCount+3)/4;
                    analogPinBytes  = (analogPinCount * 10 + 7)/8; //(groups * 5) + (analogPinCount % 4) + 1;
                    byte[] payload = new byte[analogPinCount+1];
                    payload[0] = (byte)analogPinCount;
                    Bytes.readFully(in, payload, 1);
                    return new IOIOPacket(message, payload);

                case Constants.REPORT_ANALOG_STATUS:
                    return new IOIOPacket(message, Bytes.readBytes(in, analogPinBytes));

                case Constants.SET_ANALOG_INPUT:
                    return new IOIOPacket(message, Bytes.readBytes(in, 1));
            }
            return null;
        }
    };
}
