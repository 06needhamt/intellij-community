package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author abreslav
 */
public class FunctionDescriptorUtil {
    /** @return Minimal number of arguments to be passed */
    public static int getMinimumArity(@NotNull FunctionDescriptor functionDescriptor) {
        int result = 0;
        for (ValueParameterDescriptor valueParameter : functionDescriptor.getUnsubstitutedValueParameters()) {
            if (valueParameter.hasDefaultValue()) {
                break;
            }
            result++;
        }
        return result;
    }

    /**
     * @return Maximum number of arguments that can be passed. -1 if unbound (vararg)
     */
    public static int getMaximumArity(@NotNull FunctionDescriptor functionDescriptor) {
        List<ValueParameterDescriptor> unsubstitutedValueParameters = functionDescriptor.getUnsubstitutedValueParameters();
        if (unsubstitutedValueParameters.isEmpty()) {
            return 0;
        }
        // TODO : check somewhere that vararg is only the last one, and that varargs do not have default values

        ValueParameterDescriptor lastParameter = unsubstitutedValueParameters.get(unsubstitutedValueParameters.size() - 1);
        if (lastParameter.isVararg()) {
            return -1;
        }
        return unsubstitutedValueParameters.size();
    }

    private static Map<TypeConstructor, TypeProjection> createSubstitutionContext(@NotNull FunctionDescriptor functionDescriptor, List<JetType> typeArguments) {
        if (functionDescriptor.getTypeParameters().isEmpty()) return Collections.emptyMap();

        Map<TypeConstructor, TypeProjection> result = new HashMap<TypeConstructor, TypeProjection>();

        int typeArgumentsSize = typeArguments.size();
        List<TypeParameterDescriptor> typeParameters = functionDescriptor.getTypeParameters();
        assert typeArgumentsSize == typeParameters.size();
        for (int i = 0; i < typeArgumentsSize; i++) {
            TypeParameterDescriptor typeParameterDescriptor = typeParameters.get(i);
            JetType typeArgument = typeArguments.get(i);
            result.put(typeParameterDescriptor.getTypeConstructor(), new TypeProjection(typeArgument));
        }
        return result;
    }

    @Nullable
    private static List<ValueParameterDescriptor> getSubstitutedValueParameters(FunctionDescriptor substitutedDescriptor, @NotNull FunctionDescriptor functionDescriptor, Map<TypeConstructor, TypeProjection> substitutionContext, TypeSubstitutor typeSubstitutor) {
        List<ValueParameterDescriptor> result = new ArrayList<ValueParameterDescriptor>();
        List<ValueParameterDescriptor> unsubstitutedValueParameters = functionDescriptor.getUnsubstitutedValueParameters();
        for (int i = 0, unsubstitutedValueParametersSize = unsubstitutedValueParameters.size(); i < unsubstitutedValueParametersSize; i++) {
            ValueParameterDescriptor unsubstitutedValueParameter = unsubstitutedValueParameters.get(i);
            // TODO : Lazy?
            JetType substitutedType = typeSubstitutor.substitute(substitutionContext, unsubstitutedValueParameter.getType(), Variance.IN_VARIANCE);
            if (substitutedType == null) return null;
            result.add(new ValueParameterDescriptorImpl(
                    substitutedDescriptor,
                    i,
                    unsubstitutedValueParameter.getAttributes(),
                    unsubstitutedValueParameter.getName(),
                    substitutedType,
                    unsubstitutedValueParameter.hasDefaultValue(),
                    unsubstitutedValueParameter.isVararg()
            ));
        }
        return result;
    }

    @Nullable
    private static JetType getSubstitutedReturnType(@NotNull FunctionDescriptor functionDescriptor, Map<TypeConstructor, TypeProjection> substitutionContext, TypeSubstitutor typeSubstitutor) {
        return typeSubstitutor.substitute(substitutionContext, functionDescriptor.getUnsubstitutedReturnType(), Variance.OUT_VARIANCE);
    }

    @Nullable
    public static FunctionDescriptor substituteFunctionDescriptor(@NotNull List<JetType> typeArguments, @NotNull FunctionDescriptor functionDescriptor) {
        Map<TypeConstructor, TypeProjection> substitutionContext = createSubstitutionContext(functionDescriptor, typeArguments);
        return substituteFunctionDescriptor(functionDescriptor, substitutionContext, TypeSubstitutor.INSTANCE);
    }

    @Nullable
    public static FunctionDescriptor substituteFunctionDescriptor(FunctionDescriptor functionDescriptor, Map<TypeConstructor, TypeProjection> substitutionContext, TypeSubstitutor typeSubstitutor) {
        if (substitutionContext.isEmpty()) {
            return functionDescriptor;
        }
        FunctionDescriptorImpl substitutedDescriptor = new FunctionDescriptorImpl(
                functionDescriptor,
                // TODO : safeSubstitute
                functionDescriptor.getAttributes(),
                functionDescriptor.getName());

        List<ValueParameterDescriptor> substitutedValueParameters = getSubstitutedValueParameters(substitutedDescriptor, functionDescriptor, substitutionContext, typeSubstitutor);
        if (substitutedValueParameters == null) {
            return null;
        }

        JetType substitutedReturnType = getSubstitutedReturnType(functionDescriptor, substitutionContext, typeSubstitutor);
        if (substitutedReturnType == null) {
            return null;
        }

        substitutedDescriptor.initialize(
                Collections.<TypeParameterDescriptor>emptyList(), // TODO : questionable
                substitutedValueParameters,
                substitutedReturnType
        );
        return substitutedDescriptor;
    }
}
