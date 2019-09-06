package io.fabric8.podset.operator.util;

import java.io.*;

public class DeepCopy {
    public static <T> T copy(T orignal) {
        if (orignal == null) {
            return null;
        }

        try {
            // Write the object to a byte array
            ByteArrayOutputStream fbos = new ByteArrayOutputStream();

            try (ObjectOutputStream out = new ObjectOutputStream(fbos)) {
                out.writeObject(orignal);
                out.flush();
            }

            // Retrieve an input stream from the byte array and read
            // a copy of the object back in.
            try (ByteArrayInputStream fbis = new ByteArrayInputStream(fbos.toByteArray());
                 ObjectInputStream in = new ObjectInputStream(fbis)) {
                return (T) in.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Cannot copy " + orignal, e);
        }
    }
}
