package jp.kumi.picsar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class PicsParserTest {
    @Test
    public void parsesType2SignalsAndIntersection() {
        byte[] payload = new byte[]{
                0, 0, 2, 0, 0, 0, 0, 0, 0, 1,
                0x73, 0x71, (byte) 0x84, (byte) 0x84, (byte) 0x84, (byte) 0x84
        };
        PicsParser.PicsFrame frame = PicsParser.parseType2(payload);

        assertEquals("00000001", frame.intersectionId);
        assertEquals(PicsParser.SignalState.GREEN, frame.signalOne.state);
        assertEquals(PicsParser.SignalState.RED, frame.signalTwo.state);
        assertEquals(8, frame.signalOne.remaining);
        assertEquals(8, frame.signalTwo.remaining);
        assertEquals(0x73, frame.signalOne.raw);
    }

    @Test
    public void decodesBlinkingAndUnknownRemaining() {
        byte[] payload = new byte[]{0, 0, 2, 0, 0, 0, 0, 0, 0, 1, (byte) 0x82, (byte) 0x84};
        PicsParser.PicsFrame frame = PicsParser.parseType2(payload);

        assertEquals(PicsParser.SignalState.BLINK_GREEN, frame.signalOne.state);
        assertEquals(-1, frame.signalOne.remaining);
        assertEquals(PicsParser.SignalState.NONE, frame.signalTwo.state);
    }

    @Test
    public void ignoresOtherPicsMessageTypes() {
        byte[] payload = new byte[]{0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0};
        assertNull(PicsParser.parseType2(payload));
    }

    @Test
    public void createsAllRedDemoFrame() {
        PicsParser.PicsFrame frame = PicsParser.demoAllRedFrame();
        assertEquals(PicsParser.SignalState.RED, frame.signalOne.state);
        assertEquals(PicsParser.SignalState.RED, frame.signalTwo.state);
        assertEquals(0x71, frame.signalOne.raw);
        assertEquals(0x71, frame.signalTwo.raw);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortPackets() {
        PicsParser.parseType2(new byte[]{0, 0, 2});
    }
}
