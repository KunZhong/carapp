package com.example.user.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/*
 * I don't really understand and want to know what the hell it does!
 * Maybe one day I will refactor it ;-)
 * <p/>
 * https://code.google.com/archive/p/android-camera-axis
 */
public class MjpegInputStream extends  DataInputStream{

    private final byte[] SOI_MARKER = {(byte) 0xFF, (byte) 0xD8};
    private final byte[] EOF_MARKER = {(byte) 0xFF, (byte) 0xD9};
    private final String CONTENT_LENGTH = "Content-Length";
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 40000 + HEADER_MAX_LENGTH;
    private int mContentLength = -1;

    // no more accessible
    MjpegInputStream(InputStream in) {

        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }

    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence) throws IOException {
        int seqIndex = 0;
        byte c;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
            c = (byte) in.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                seqIndex++;
                if (seqIndex == sequence.length) {
                    return i + 1;
                }
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int end = getEndOfSeqeunce(in, sequence);
        return (end < 0) ? (-1) : (end - sequence.length);
    }

    private int parseContentLength(byte[] headerBytes) throws IOException, NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }

    // no more accessible
    Bitmap readMjpegFrame() throws IOException {

        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();
        byte[] header = new byte[headerLen];
        readFully(header);
        try {
            mContentLength = parseContentLength(header);
        } catch (NumberFormatException nfe) {
            mContentLength = getEndOfSeqeunce(this, EOF_MARKER);
        }
        reset();
        byte[] frameData = new byte[mContentLength];
        skipBytes(headerLen);
        readFully(frameData);
        return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData));
    }

    public static byte[]  myread(DataInputStream bin, int size, int max) {
        byte[] image = new byte[size];
        int hasRead = 0;
        while (true) {
            if (max > size - hasRead) {
                max = size - hasRead;
            }
            try {
                hasRead = hasRead + bin.read(image, hasRead, max);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("mjpegInput", "myread: ");
            }
            if (hasRead == size) {
                break;
            }
        }
        return image;
    }
    Bitmap readMjpegFrame2() throws IOException{

        // 1，从输入流中取20个字节，这20个字节表示当前图片的长度
        byte[] imageSize_Byte = myread(this, 10, 4096);

        // 2，把取出来的数据转为字符串，字符串的格式是 数字+len,比如一个图片的长度是10
        // 那么现在的字符串就是"20len"
        String imageSize_String = new String(imageSize_Byte);

        // 3，我们需要的是字符串中的数字，因为我们获取这个数字的目的是为了获取图片
        // /所以我们现在需要把字符串后面的len去掉
        imageSize_String = imageSize_String.substring(0,
                imageSize_String.indexOf("len"));

        // 4，现在我们获取 图片长度是一个字符串，但是我们需要的是整型，所以我们现在把字符串转为整型
        int imageSize_int = new Integer(imageSize_String);

        // 5，计算出图片长度后，开始获取代表图片的字节
        byte[] image = myread(this, imageSize_int, 4096);// 读取图片

        return BitmapFactory.decodeByteArray(image, 0, image.length);

    }
}