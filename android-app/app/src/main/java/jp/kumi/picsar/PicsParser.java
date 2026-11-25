package jp.kumi.picsar;

import java.util.Locale;

public final class PicsParser {
    public static final int MANUFACTURER_ID = 0x01CE;

    private PicsParser() {}

    public enum SignalState {
        NO_SIGNAL(0, "信号なし"),
        RED(1, "赤"),
        BLINK_GREEN(2, "青点滅"),
        GREEN(3, "青"),
        NONE(4, "対象なし"),
        UNKNOWN(-1, "不明");

        public final int code;
        public final String label;

        SignalState(int code, String label) {
            this.code = code;
            this.label = label;
        }

        static SignalState fromCode(int code) {
            for (SignalState state : values()) {
                if (state.code == code) return state;
            }
            return UNKNOWN;
        }
    }

    public static final class Signal {
        public final SignalState state;
        public final int remaining;
        public final int raw;

        Signal(SignalState state, int remaining, int raw) {
            this.state = state;
            this.remaining = remaining;
            this.raw = raw;
        }
    }

    public static final class PicsFrame {
        public final String intersectionId;
        public final Signal signalOne;
        public final Signal signalTwo;
        public final String rawHex;

        PicsFrame(String intersectionId, Signal signalOne, Signal signalTwo, String rawHex) {
            this.intersectionId = intersectionId;
            this.signalOne = signalOne;
            this.signalTwo = signalTwo;
            this.rawHex = rawHex;
        }
    }

    public static PicsFrame parseType2(byte[] payload) {
        if (payload == null || payload.length < 11) {
            throw new IllegalArgumentException("PICS Type 2 payload must contain at least 11 bytes");
        }
        int type = payload[2] & 0xFF;
        if (type != 2) return null;

        String id = String.format(
                Locale.US,
                "%02X%02X%02X%02X",
                payload[6] & 0xFF,
                payload[7] & 0xFF,
                payload[8] & 0xFF,
                payload[9] & 0xFF
        );
        Signal one = decodeSignal(payload[10] & 0xFF);
        Signal two = payload.length > 11
                ? decodeSignal(payload[11] & 0xFF)
                : new Signal(SignalState.UNKNOWN, -1, 0);
        return new PicsFrame(id, one, two, hex(payload));
    }

    public static PicsFrame demoFrame(int step) {
        byte[] payload = new byte[16];
        payload[2] = 2;
        payload[3] = (byte) step;
        payload[9] = 1;
        int phase = Math.floorMod(step, 24);
        int first = phase < 8 ? 3 : phase < 12 ? 2 : 1;
        int second = phase < 12 ? 1 : phase < 20 ? 3 : 2;
        int remaining = (7 - (step % 8)) << 4;
        payload[10] = (byte) (remaining | first);
        payload[11] = (byte) (remaining | second);
        for (int i = 12; i < 16; i++) payload[i] = (byte) 0x84;
        return parseType2(payload);
    }

    public static PicsFrame demoAllRedFrame() {
        byte[] payload = new byte[16];
        payload[2] = 2;
        payload[9] = 1;
        payload[10] = 0x71;
        payload[11] = 0x71;
        for (int i = 12; i < 16; i++) payload[i] = (byte) 0x84;
        return parseType2(payload);
    }

    private static Signal decodeSignal(int value) {
        int remainingNibble = (value >> 4) & 0x0F;
        int remaining = remainingNibble >= 8 ? -1 : remainingNibble + 1;
        return new Signal(SignalState.fromCode(value & 0x0F), remaining, value);
    }

    private static String hex(byte[] payload) {
        StringBuilder result = new StringBuilder(payload.length * 3);
        for (int i = 0; i < payload.length; i++) {
            if (i > 0) result.append(' ');
            result.append(String.format(Locale.US, "%02X", payload[i] & 0xFF));
        }
        return result.toString();
    }
}
