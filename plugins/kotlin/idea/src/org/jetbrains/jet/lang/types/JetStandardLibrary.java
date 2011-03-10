package org.jetbrains.jet.lang.types;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.ErrorHandler;
import org.jetbrains.jet.lang.JetFileType;
import org.jetbrains.jet.lang.JetSemanticServices;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingTraceContext;
import org.jetbrains.jet.lang.resolve.JetScope;
import org.jetbrains.jet.lang.resolve.TopDownAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author abreslav
 */
public class JetStandardLibrary {

    // TODO : consider releasing this memory
    private static final Map<Project, JetStandardLibrary> standardLibraryCache = new HashMap<Project, JetStandardLibrary>();

    public static JetStandardLibrary getJetStandardLibrary(@NotNull Project project) {
        JetStandardLibrary standardLibrary = standardLibraryCache.get(project);
        if (standardLibrary == null) {
            standardLibrary = new JetStandardLibrary(project);
            standardLibraryCache.put(project, standardLibrary);
        }
        return standardLibrary;
    }

    private final JetScope libraryScope;
    private final ClassDescriptor byteClass;
    private final ClassDescriptor charClass;
    private final ClassDescriptor shortClass;
    private final ClassDescriptor intClass;
    private final ClassDescriptor longClass;
    private final ClassDescriptor floatClass;
    private final ClassDescriptor doubleClass;
    private final ClassDescriptor booleanClass;
    private final ClassDescriptor stringClass;
    private final ClassDescriptor arrayClass;

    private final Type byteType;
    private final Type charType;
    private final Type shortType;
    private final Type intType;
    private final Type longType;
    private final Type floatType;
    private final Type doubleType;
    private final Type booleanType;
    private final Type stringType;

    private JetStandardLibrary(@NotNull Project project) {
        // TODO : review
        InputStream stream = JetStandardClasses.class.getClassLoader().getResourceAsStream("jet/lang/Library.jet");
        try {
            //noinspection IOResourceOpenedButNotSafelyClosed
            JetFile file = (JetFile) PsiFileFactory.getInstance(project).createFileFromText("Library.jet",
                    JetFileType.INSTANCE, FileUtil.loadTextAndClose(new InputStreamReader(stream)));

            JetSemanticServices bootstrappingSemanticServices = JetSemanticServices.createSemanticServices(this, ErrorHandler.DO_NOTHING);
            BindingTraceContext bindingTraceContext = new BindingTraceContext();
            TopDownAnalyzer bootstrappingTDA = new TopDownAnalyzer(bootstrappingSemanticServices, bindingTraceContext);
            bootstrappingTDA.process(JetStandardClasses.STANDARD_CLASSES, file.getRootNamespace().getDeclarations());
            BindingContext bindingContext = bindingTraceContext;

            this.libraryScope = bindingContext.getTopLevelScope();

            this.byteClass = libraryScope.getClass("Byte");
            this.charClass = libraryScope.getClass("Char");
            this.shortClass = libraryScope.getClass("Short");
            this.intClass = libraryScope.getClass("Int");
            this.longClass = libraryScope.getClass("Long");
            this.floatClass = libraryScope.getClass("Float");
            this.doubleClass = libraryScope.getClass("Double");
            this.booleanClass = libraryScope.getClass("Boolean");
            this.stringClass = libraryScope.getClass("String");
            this.arrayClass = libraryScope.getClass("Array");

            this.byteType = new TypeImpl(getByte());
            this.charType = new TypeImpl(getChar());
            this.shortType = new TypeImpl(getShort());
            this.intType = new TypeImpl(getInt());
            this.longType = new TypeImpl(getLong());
            this.floatType = new TypeImpl(getFloat());
            this.doubleType = new TypeImpl(getDouble());
            this.booleanType = new TypeImpl(getBoolean());
            this.stringType = new TypeImpl(getString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public JetScope getLibraryScope() {
        return libraryScope;
    }

    @NotNull
    public ClassDescriptor getByte() {
        return byteClass;
    }

    @NotNull
    public ClassDescriptor getChar() {
        return charClass;
    }

    @NotNull
    public ClassDescriptor getShort() {
        return shortClass;
    }

    @NotNull
    public ClassDescriptor getInt() {
        return intClass;
    }

    @NotNull
    public ClassDescriptor getLong() {
        return longClass;
    }

    @NotNull
    public ClassDescriptor getFloat() {
        return floatClass;
    }

    @NotNull
    public ClassDescriptor getDouble() {
        return doubleClass;
    }

    @NotNull
    public ClassDescriptor getBoolean() {
        return booleanClass;
    }

    @NotNull
    public ClassDescriptor getString() {
        return stringClass;
    }

    @NotNull
    public ClassDescriptor getArray() {
        return arrayClass;
    }

    @NotNull
    public Type getIntType() {
        return intType;
    }

    @NotNull
    public Type getLongType() {
        return longType;
    }

    @NotNull
    public Type getDoubleType() {
        return doubleType;
    }

    @NotNull
    public Type getFloatType() {
        return floatType;
    }

    @NotNull
    public Type getCharType() {
        return charType;
    }

    @NotNull
    public Type getBooleanType() {
        return booleanType;
    }

    @NotNull
    public Type getStringType() {
        return stringType;
    }

    @NotNull
    public Type getByteType() {
        return byteType;
    }

    @NotNull
    public Type getShortType() {
        return shortType;
    }

    @NotNull
    public Type getArrayType(@NotNull Type argument) {
        Variance variance = Variance.INVARIANT;
        return getArrayType(variance, argument);
    }

    @NotNull
    public Type getArrayType(@NotNull Variance variance, @NotNull Type argument) {
        List<TypeProjection> types = Collections.singletonList(new TypeProjection(variance, argument));
        return new TypeImpl(
                Collections.<Attribute>emptyList(),
                getArray().getTypeConstructor(),
                false,
                types,
                getArray().getMemberScope(types)
        );
    }
}
