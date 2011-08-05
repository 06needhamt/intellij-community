package org.jetbrains.jet.lang.resolve.constants;

import com.google.common.base.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationArgumentVisitor;
import org.jetbrains.jet.lang.types.JetStandardLibrary;
import org.jetbrains.jet.lang.types.JetType;

/**
 * @author abreslav
 */
public class ByteValue implements CompileTimeConstant<Byte> {
    public static final Function<Long, ByteValue> CREATE = new Function<Long, ByteValue>() {
        @Override
        public ByteValue apply(@Nullable Long input) {
            assert input != null;
            return new ByteValue(input.byteValue());
        }
    };

    private final byte value;

    public ByteValue(byte value) {
        this.value = value;
    }

    @Override
    public Byte getValue() {
        return value;
    }

    @NotNull
    @Override
    public JetType getType(@NotNull JetStandardLibrary standardLibrary) {
        return standardLibrary.getByteType();
    }

    @Override
    public <R, D> R accept(AnnotationArgumentVisitor<R, D> visitor, D data) {
        return visitor.visitByteValue(this, data);
    }

    @Override
    public String toString() {
        return value + ".byt";
    }
}
