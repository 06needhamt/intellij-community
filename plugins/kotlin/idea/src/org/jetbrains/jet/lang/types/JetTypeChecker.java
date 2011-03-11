package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.JetExpression;

import java.util.*;

/**
 * @author abreslav
 */
public class JetTypeChecker {

    private final Map<TypeConstructor, Set<TypeConstructor>> conversionMap = new HashMap<TypeConstructor, Set<TypeConstructor>>();
    private final JetStandardLibrary standardLibrary;

    public JetTypeChecker(JetStandardLibrary standardLibrary) {
        this.standardLibrary = standardLibrary;
    }

    private Map<TypeConstructor, Set<TypeConstructor>> getConversionMap() {
        if (conversionMap.size() == 0) {
            addConversion(standardLibrary.getByte(),
                    standardLibrary.getShort(),
                    standardLibrary.getInt(),
                    standardLibrary.getLong(),
                    standardLibrary.getFloat(),
                    standardLibrary.getDouble());

            addConversion(standardLibrary.getShort(),
                    standardLibrary.getInt(),
                    standardLibrary.getLong(),
                    standardLibrary.getFloat(),
                    standardLibrary.getDouble());

            addConversion(standardLibrary.getChar(),
                    standardLibrary.getInt(),
                    standardLibrary.getLong(),
                    standardLibrary.getFloat(),
                    standardLibrary.getDouble());

            addConversion(standardLibrary.getInt(),
                    standardLibrary.getLong(),
                    standardLibrary.getFloat(),
                    standardLibrary.getDouble());

            addConversion(standardLibrary.getLong(),
                    standardLibrary.getFloat(),
                    standardLibrary.getDouble());

            addConversion(standardLibrary.getFloat(),
                    standardLibrary.getDouble());
        }
        return conversionMap;
    }

    private void addConversion(ClassDescriptor actual, ClassDescriptor... convertedTo) {
        TypeConstructor[] constructors = new TypeConstructor[convertedTo.length];
        for (int i = 0, convertedToLength = convertedTo.length; i < convertedToLength; i++) {
            ClassDescriptor classDescriptor = convertedTo[i];
            constructors[i] = classDescriptor.getTypeConstructor();
        }
        conversionMap.put(actual.getTypeConstructor(), new HashSet<TypeConstructor>(Arrays.asList(constructors)));
    }

    public Type commonSupertype(Collection<Type> types) {
        Collection<Type> typeSet = new HashSet<Type>(types);
        assert !typeSet.isEmpty();
        boolean nullable = false;
        for (Iterator<Type> iterator = typeSet.iterator(); iterator.hasNext();) {
            Type type = iterator.next();
            if (JetStandardClasses.isNothing(type)) {
                iterator.remove();
            }
            nullable |= type.isNullable();
        }

        if (typeSet.isEmpty()) {
            // TODO : attributes
            return nullable ? JetStandardClasses.getNullableNothingType() : JetStandardClasses.getNothingType();
        }

        if (typeSet.size() == 1) {
            return TypeUtils.makeNullableIfNeeded(typeSet.iterator().next(), nullable);
        }

        Map<TypeConstructor, Set<Type>> commonSupertypes = computeCommonRawSupertypes(typeSet);
        while (commonSupertypes.size() > 1) {
            HashSet<Type> merge = new HashSet<Type>();
            for (Set<Type> supertypes : commonSupertypes.values()) {
                merge.addAll(supertypes);
            }
            commonSupertypes = computeCommonRawSupertypes(merge);
        }
        assert !commonSupertypes.isEmpty();
        Map.Entry<TypeConstructor, Set<Type>> entry = commonSupertypes.entrySet().iterator().next();
        Type result = computeSupertypeProjections(entry.getKey(), entry.getValue());

        return TypeUtils.makeNullableIfNeeded(result, nullable);
    }

    private Type computeSupertypeProjections(TypeConstructor constructor, Set<Type> types) {
        // we assume that all the given types are applications of the same type constructor

        assert !types.isEmpty();

        if (types.size() == 1) {
            return types.iterator().next();
        }

        List<TypeParameterDescriptor> parameters = constructor.getParameters();
        List<TypeProjection> newProjections = new ArrayList<TypeProjection>();
        for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
            TypeParameterDescriptor parameterDescriptor = parameters.get(i);
            Set<TypeProjection> typeProjections = new HashSet<TypeProjection>();
            for (Type type : types) {
                typeProjections.add(type.getArguments().get(i));
            }
            newProjections.add(computeSupertypeProjection(parameterDescriptor, typeProjections));
        }

        boolean nullable = false;
        for (Type type : types) {
            nullable |= type.isNullable();
        }

        // TODO : attributes?
        return new TypeImpl(Collections.<Attribute>emptyList(), constructor, nullable, newProjections, JetStandardClasses.STUB);
    }

    private TypeProjection computeSupertypeProjection(TypeParameterDescriptor parameterDescriptor, Set<TypeProjection> typeProjections) {
        if (typeProjections.size() == 1) {
            return typeProjections.iterator().next();
        }

        Set<Type> ins = new HashSet<Type>();
        Set<Type> outs = new HashSet<Type>();

        Variance variance = parameterDescriptor.getVariance();
        switch (variance) {
            case INVARIANT:
                // Nothing
                break;
            case IN_VARIANCE:
                outs = null;
                break;
            case OUT_VARIANCE:
                ins = null;
                break;
        }

        for (TypeProjection projection : typeProjections) {
            Variance projectionKind = projection.getProjectionKind();
            if (projectionKind.allowsInPosition()) {
                if (ins != null) {
                    ins.add(projection.getType());
                }
            } else {
                ins = null;
            }

            if (projectionKind.allowsOutPosition()) {
                if (outs != null) {
                    outs.add(projection.getType());
                }
            } else {
                outs = null;
            }
        }

        if (ins != null) {
            Variance projectionKind = variance == Variance.IN_VARIANCE ? Variance.INVARIANT : Variance.IN_VARIANCE;
            Type intersection = TypeUtils.intersect(this, ins);
            if (intersection == null) {
                return new TypeProjection(Variance.OUT_VARIANCE, commonSupertype(parameterDescriptor.getUpperBounds()));
            }
            return new TypeProjection(projectionKind, intersection);
        } else if (outs != null) {
            Variance projectionKind = variance == Variance.OUT_VARIANCE ? Variance.INVARIANT : Variance.OUT_VARIANCE;
            return new TypeProjection(projectionKind, commonSupertype(outs));
        } else {
            Variance projectionKind = variance == Variance.OUT_VARIANCE ? Variance.INVARIANT : Variance.OUT_VARIANCE;
            return new TypeProjection(projectionKind, commonSupertype(parameterDescriptor.getUpperBounds()));
        }
    }

    private Map<TypeConstructor, Set<Type>> computeCommonRawSupertypes(Collection<Type> types) {
        assert !types.isEmpty();

        final Map<TypeConstructor, Set<Type>> constructorToAllInstances = new HashMap<TypeConstructor, Set<Type>>();
        Set<TypeConstructor> commonSuperclasses = null;

        List<TypeConstructor> order = null;
        for (Type type : types) {
            Set<TypeConstructor> visited = new HashSet<TypeConstructor>();

            order = dfs(type, visited, new DfsNodeHandler<List<TypeConstructor>>() {
                public LinkedList<TypeConstructor> list = new LinkedList<TypeConstructor>();

                @Override
                public void beforeChildren(Type current) {
                    TypeConstructor constructor = current.getConstructor();

                    Set<Type> instances = constructorToAllInstances.get(constructor);
                    if (instances == null) {
                        instances = new HashSet<Type>();
                        constructorToAllInstances.put(constructor, instances);
                    }
                    instances.add(current);
                }

                @Override
                public void afterChildren(Type current) {
                    list.addFirst(current.getConstructor());
                }

                @Override
                public List<TypeConstructor> result() {
                    return list;
                }
            });

            if (commonSuperclasses == null) {
                commonSuperclasses = visited;
            }
            else {
                commonSuperclasses.retainAll(visited);
            }
        }
        assert order != null;

        Set<TypeConstructor> notSource = new HashSet<TypeConstructor>();
        Map<TypeConstructor, Set<Type>> result = new HashMap<TypeConstructor, Set<Type>>();
        for (TypeConstructor superConstructor : order) {
            if (!commonSuperclasses.contains(superConstructor)) {
                continue;
            }

            if (!notSource.contains(superConstructor)) {
                result.put(superConstructor, constructorToAllInstances.get(superConstructor));
                markAll(superConstructor, notSource);
            }
        }

        return result;
    }

    private void markAll(TypeConstructor typeConstructor, Set<TypeConstructor> markerSet) {
        markerSet.add(typeConstructor);
        for (Type type : typeConstructor.getSupertypes()) {
            markAll(type.getConstructor(), markerSet);
        }
    }

    private <R> R dfs(Type current, Set<TypeConstructor> visited, DfsNodeHandler<R> handler) {
        doDfs(current, visited, handler);
        return handler.result();
    }

    private void doDfs(Type current, Set<TypeConstructor> visited, DfsNodeHandler<?> handler) {
        if (!visited.add(current.getConstructor())) {
            return;
        }
        handler.beforeChildren(current);
        Map<TypeConstructor, TypeProjection> substitutionContext = TypeSubstitutor.INSTANCE.getSubstitutionContext(current);
        for (Type supertype : current.getConstructor().getSupertypes()) {
            TypeConstructor supertypeConstructor = supertype.getConstructor();
            if (visited.contains(supertypeConstructor)) {
                continue;
            }
            Type substitutedSupertype = TypeSubstitutor.INSTANCE.substitute(substitutionContext, supertype, Variance.INVARIANT);
            dfs(substitutedSupertype, visited, handler);
        }
        handler.afterChildren(current);
    }

    public boolean isConvertibleTo(JetExpression expression, Type type) {
        throw new UnsupportedOperationException(); // TODO
    }

    public boolean isConvertibleTo(Type actual, Type expected) {
        if (isSubtypeOf(actual, expected)) return true;
        if (expected.getConstructor() == JetStandardClasses.getTuple(0).getTypeConstructor()) {
            return true;
        }
        if (actual.getArguments().isEmpty()) {
            TypeConstructor actualConstructor = actual.getConstructor();
            TypeConstructor constructor = expected.getConstructor();
            Set<TypeConstructor> convertibleTo = getConversionMap().get(actualConstructor);
            if (convertibleTo != null) {
                return convertibleTo.contains(constructor);
            }
        }
        return false;
    }

    public boolean isSubtypeOf(@NotNull Type subtype, @NotNull Type supertype) {
        if (!supertype.isNullable() && subtype.isNullable()) {
            return false;
        }
        if (JetStandardClasses.isNothing(subtype)) {
            return true;
        }
        @Nullable Type closestSupertype = findCorrespondingSupertype(subtype, supertype);
        if (closestSupertype == null) {
            return false;
        }

        return checkSubtypeForTheSameConstructor(closestSupertype, supertype);
    }

    // This method returns the supertype of the first parameter that has the same constructor
    // as the second parameter, applying the substitution of type arguments to it
    @Nullable
    private Type findCorrespondingSupertype(Type subtype, Type supertype) {
        TypeConstructor constructor = subtype.getConstructor();
        if (constructor.equals(supertype.getConstructor())) {
            return subtype;
        }
        for (Type immediateSupertype : constructor.getSupertypes()) {
            Type correspondingSupertype = findCorrespondingSupertype(immediateSupertype, supertype);
            if (correspondingSupertype != null) {
                return TypeSubstitutor.INSTANCE.substitute(subtype, correspondingSupertype, Variance.INVARIANT);
            }
        }
        return null;
    }

    private boolean checkSubtypeForTheSameConstructor(Type subtype, Type supertype) {
        TypeConstructor constructor = subtype.getConstructor();
        assert constructor.equals(supertype.getConstructor());

        List<TypeProjection> subArguments = subtype.getArguments();
        List<TypeProjection> superArguments = supertype.getArguments();
        List<TypeParameterDescriptor> parameters = constructor.getParameters();
        for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
            TypeParameterDescriptor parameter = parameters.get(i);
            TypeProjection subArgument = subArguments.get(i);
            TypeProjection superArgument = superArguments.get(i);

            Type subArgumentType = subArgument.getType();
            Type superArgumentType = superArgument.getType();
            switch (parameter.getVariance()) {
                case INVARIANT:
                    switch (superArgument.getProjectionKind()) {
                        case INVARIANT:
                            if (!TypeImpl.equalTypes(subArgumentType, superArgumentType)) {
                                return false;
                            }
                            break;
                        case OUT_VARIANCE:
                            if (!subArgument.getProjectionKind().allowsOutPosition()) {
                                return false;
                            }
                            if (!isSubtypeOf(subArgumentType, superArgumentType)) {
                                return false;
                            }
                            break;
                        case IN_VARIANCE:
                            if (!subArgument.getProjectionKind().allowsInPosition()) {
                                return false;
                            }
                            if (!isSubtypeOf(superArgumentType, subArgumentType)) {
                                return false;
                            }
                            break;
                    }
                    break;
                case IN_VARIANCE:
                    switch (superArgument.getProjectionKind()) {
                        case INVARIANT:
                        case IN_VARIANCE:
                            if (!isSubtypeOf(superArgumentType, subArgumentType)) {
                                return false;
                            }
                            break;
                        case OUT_VARIANCE:
                            if (!isSubtypeOf(subArgumentType, superArgumentType)) {
                                return false;
                            }
                            break;
                    }
                    break;
                case OUT_VARIANCE:
                    switch (superArgument.getProjectionKind()) {
                        case INVARIANT:
                        case OUT_VARIANCE:
                        case IN_VARIANCE:
                            if (!isSubtypeOf(subArgumentType, superArgumentType)) {
                                return false;
                            }
                            break;
                    }
                    break;
            }
        }
        return true;
    }
}