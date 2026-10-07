package net.kdt.pojavlaunch.servers;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Just enough NBT to read servers.dat (uncompressed): compounds become maps, lists become lists. */
final class NbtReader {
    private NbtReader() {}

    static Map<String, Object> readRoot(InputStream inputStream) throws IOException {
        DataInputStream input = new DataInputStream(inputStream);
        int type = input.readUnsignedByte();
        if(type != 10) throw new IOException("Root tag is not a compound");
        input.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) readPayload(input, type, 0);
        return root;
    }

    private static Object readPayload(DataInputStream input, int type, int depth) throws IOException {
        if(depth > 64) throw new IOException("NBT too deep");
        switch (type) {
            case 1: return input.readByte();
            case 2: return input.readShort();
            case 3: return input.readInt();
            case 4: return input.readLong();
            case 5: return input.readFloat();
            case 6: return input.readDouble();
            case 7: {
                byte[] bytes = new byte[checkedLength(input.readInt())];
                input.readFully(bytes);
                return bytes;
            }
            case 8: return input.readUTF();
            case 9: {
                int elementType = input.readUnsignedByte();
                int length = checkedLength(input.readInt());
                List<Object> list = new ArrayList<>(Math.min(length, 1024));
                for(int i = 0; i < length; i++) list.add(readPayload(input, elementType, depth + 1));
                return list;
            }
            case 10: {
                Map<String, Object> compound = new HashMap<>();
                while(true) {
                    int childType = input.readUnsignedByte();
                    if(childType == 0) return compound;
                    String name = input.readUTF();
                    compound.put(name, readPayload(input, childType, depth + 1));
                }
            }
            case 11: {
                int[] ints = new int[checkedLength(input.readInt())];
                for(int i = 0; i < ints.length; i++) ints[i] = input.readInt();
                return ints;
            }
            case 12: {
                long[] longs = new long[checkedLength(input.readInt())];
                for(int i = 0; i < longs.length; i++) longs[i] = input.readLong();
                return longs;
            }
            default: throw new IOException("Unknown NBT tag " + type);
        }
    }

    private static int checkedLength(int length) throws IOException {
        if(length < 0 || length > 16 * 1024 * 1024) throw new IOException("Bad NBT length " + length);
        return length;
    }
}
