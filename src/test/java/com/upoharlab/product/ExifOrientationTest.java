package com.upoharlab.product;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExifOrientationTest {
  static byte[] jpegWithOrientation(int width, int height, int orientation, boolean little)
      throws Exception {
    var plain = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "JPEG", plain);
    byte[] jpeg = plain.toByteArray();
    byte o = (byte) orientation;
    byte[] tiff =
        little
            ? new byte[] {
              'E', 'x', 'i', 'f', 0, 0, 'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 1, 3, 0, 1, 0, 0,
              0, o, 0, 0, 0, 0, 0, 0, 0
            }
            : new byte[] {
              'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 1, 0x12, 0, 3, 0, 0, 0,
              1, 0, o, 0, 0, 0, 0, 0, 0
            };
    var result = new ByteArrayOutputStream();
    result.write(jpeg, 0, 2);
    result.write(0xFF);
    result.write(0xE1);
    result.write(0);
    result.write(tiff.length + 2);
    result.write(tiff);
    result.write(jpeg, 2, jpeg.length - 2);
    return result.toByteArray();
  }

  @Test
  void readsBothByteOrders() throws Exception {
    assertThat(ExifOrientation.read(jpegWithOrientation(10, 8, 6, false))).isEqualTo(6);
    assertThat(ExifOrientation.read(jpegWithOrientation(10, 8, 8, true))).isEqualTo(8);
  }

  @Test
  void badInputMeansNoRotationAndNoException() throws Exception {
    assertThat(ExifOrientation.read(new byte[0])).isEqualTo(1);
    assertThat(ExifOrientation.read("not an image".getBytes())).isEqualTo(1);
    byte[] truncated = java.util.Arrays.copyOf(jpegWithOrientation(10, 8, 6, false), 20);
    assertThat(ExifOrientation.read(truncated)).isEqualTo(1);
    assertThat(ExifOrientation.read(jpegWithOrientation(10, 8, 99, false))).isEqualTo(1);
    var plain = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 8, BufferedImage.TYPE_INT_RGB), "JPEG", plain);
    assertThat(ExifOrientation.read(plain.toByteArray())).isEqualTo(1);
  }

  /** Marks the source's top-left pixel, then checks which corner it lands in for each tag. */
  @ParameterizedTest
  @CsvSource({
    "2,3,0,4,2",
    "3,3,1,4,2",
    "4,0,1,4,2",
    "5,0,0,2,4",
    "6,1,0,2,4",
    "7,1,3,2,4",
    "8,0,3,2,4"
  })
  void movesPixelsAsTheTagRequires(int orientation, int x, int y, int width, int height) {
    var source = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
    source.setRGB(0, 0, 0xFFFF0000);
    var result = ExifOrientation.apply(source, orientation);
    assertThat(result.getWidth()).isEqualTo(width);
    assertThat(result.getHeight()).isEqualTo(height);
    assertThat(result.getRGB(x, y)).isEqualTo(0xFFFF0000);
  }
}
