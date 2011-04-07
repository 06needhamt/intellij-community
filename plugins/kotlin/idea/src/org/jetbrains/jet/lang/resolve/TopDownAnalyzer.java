package org.jetbrains.jet.lang.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.JetSemanticServices;
import org.jetbrains.jet.lang.cfg.JetControlFlowProcessor;
import org.jetbrains.jet.lang.cfg.JetFlowInformationProvider;
import org.jetbrains.jet.lang.cfg.pseudocode.*;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.types.*;

import java.util.*;

/**
 * @author abreslav
 */
public class TopDownAnalyzer {

    private final Map<JetClass, MutableClassDescriptor> classes = new LinkedHashMap<JetClass, MutableClassDescriptor>();
    private final Map<JetNamespace, WritableScope> namespaceScopes = new LinkedHashMap<JetNamespace, WritableScope>();
    private final Map<JetDeclaration, FunctionDescriptor> functions = new LinkedHashMap<JetDeclaration, FunctionDescriptor>();
    private final Map<JetDeclaration, WritableScope> declaringScopes = new HashMap<JetDeclaration, WritableScope>();

    private final JetSemanticServices semanticServices;
    private final ClassDescriptorResolver classDescriptorResolver;
    private final BindingTrace trace;
    private final JetControlFlowDataTraceFactory flowDataTraceFactory;
    private boolean readyToProcessExpressions = false;

    public TopDownAnalyzer(JetSemanticServices semanticServices, @NotNull BindingTrace bindingTrace, @NotNull JetControlFlowDataTraceFactory flowDataTraceFactory) {
        this.semanticServices = semanticServices;
        this.classDescriptorResolver = new ClassDescriptorResolver(semanticServices, bindingTrace);
        this.trace = bindingTrace;
        this.flowDataTraceFactory = flowDataTraceFactory;
    }

    public TopDownAnalyzer(JetSemanticServices semanticServices, @NotNull BindingTrace bindingTrace) {
        this.semanticServices = semanticServices;
        this.classDescriptorResolver = new ClassDescriptorResolver(semanticServices, bindingTrace);
        this.trace = bindingTrace;
        this.flowDataTraceFactory = JetControlFlowDataTraceFactory.EMPTY;
    }

    public void process(@NotNull JetScope outerScope, @NotNull JetDeclaration declaration) {
        process(outerScope, Collections.singletonList(declaration));
    }

    public void process(@NotNull JetScope outerScope, @NotNull List<JetDeclaration> declarations) {
        final WritableScope toplevelScope = semanticServices.createWritableScope(outerScope, outerScope.getContainingDeclaration()); // TODO ?!
        trace.setToplevelScope(toplevelScope); // TODO : this is a hack
        collectTypeDeclarators(toplevelScope, declarations);
        resolveTypeDeclarations();
        processBehaviorDeclarators(toplevelScope, declarations);
        readyToProcessExpressions = true;
        resolveBehaviorDeclarationBodies();
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void collectTypeDeclarators(
            @NotNull final WritableScope declaringScope,
            List<JetDeclaration> declarations) {
        for (JetDeclaration declaration : declarations) {
            declaration.accept(new JetVisitor() {
                @Override
                public void visitClass(JetClass klass) {
                    WritableScope classScope = processClass(declaringScope, klass);
                    collectTypeDeclarators(classScope, klass.getDeclarations());
                }

                @Override
                public void visitNamespace(JetNamespace namespace) {
                    List<JetImportDirective> importDirectives = namespace.getImportDirectives();

                    String name = namespace.getName();
                    if (name == null) {
                        name = "<no name provided>";
                    }
                    NamespaceDescriptor namespaceDescriptor = declaringScope.getDeclaredNamespace(name);
                    if (namespaceDescriptor == null) {
                        namespaceDescriptor = new NamespaceDescriptor(
                                declaringScope.getContainingDeclaration(),
                                Collections.<Attribute>emptyList(), // TODO
                                name
                        );
                        declaringScope.addNamespace(namespaceDescriptor);
                        trace.recordDeclarationResolution(namespace, namespaceDescriptor);
                    }

                    WritableScope namespaceScope = semanticServices.createWritableScope(declaringScope, namespaceDescriptor);
                    namespaceScopes.put(namespace, namespaceScope);

                    for (JetImportDirective importDirective : importDirectives) {
                        if (importDirective.isAbsoluteInRootNamespace()) {
                            throw new UnsupportedOperationException();
                        }
                        if (importDirective.isAllUnder()) {
                            JetExpression importedReference = importDirective.getImportedReference();
                            if (importedReference != null) {
                                JetType type = semanticServices.getTypeInferrer(trace, JetFlowInformationProvider.ERROR).getType(namespaceScope, importedReference, false);
                                if (type != null) {
                                    namespaceScope.importScope(type.getMemberScope());
                                }
                            }
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }

                    collectTypeDeclarators(namespaceScope, namespace.getDeclarations());
                }

                @Override
                public void visitTypedef(JetTypedef typedef) {
                    processTypeDef(typedef);
                }

                @Override
                public void visitDeclaration(JetDeclaration dcl) {
                    // Other declarations do not declare visible types
                }
            });
        }
    }

    private WritableScope processClass(@NotNull WritableScope declaringScope, JetClass klass) {
        MutableClassDescriptor mutableClassDescriptor = new MutableClassDescriptor(semanticServices, declaringScope.getContainingDeclaration(), declaringScope);
        mutableClassDescriptor.setName(klass.getName());

        declaringScope.addClassifierDescriptor(mutableClassDescriptor);

        classes.put(klass, mutableClassDescriptor);
        declaringScopes.put(klass, declaringScope);

        return mutableClassDescriptor.getUnsubstitutedMemberScope();
    }

    private void processExtension(JetExtension extension) {
        throw new UnsupportedOperationException(extension.getText()); // TODO
    }

    private void processTypeDef(@NotNull JetTypedef typedef) {
        throw new UnsupportedOperationException(typedef.getText()); // TODO
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void resolveTypeDeclarations() {
        for (Map.Entry<JetClass, MutableClassDescriptor> entry : classes.entrySet()) {
            JetClass jetClass = entry.getKey();
            MutableClassDescriptor descriptor = entry.getValue();
            classDescriptorResolver.resolveMutableClassDescriptor(declaringScopes.get(jetClass), jetClass, descriptor);
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void processBehaviorDeclarators(@NotNull final WritableScope declaringScope, List<JetDeclaration> declarations) {
        for (JetDeclaration declaration : declarations) {
            declaration.accept(new JetVisitor() {
                @Override
                public void visitClass(JetClass klass) {
                    MutableClassDescriptor mutableClassDescriptor = classes.get(klass);
                    processPrimaryConstructor(mutableClassDescriptor, klass);
                    processBehaviorDeclarators(mutableClassDescriptor.getUnsubstitutedMemberScope(), klass.getDeclarations());
                }

                @Override
                public void visitClassObject(JetClassObject classObject) {
                    processClassObject(classObject);
                    processBehaviorDeclarators(declaringScope, classObject.getObject().getDeclarations());
                }

                @Override
                public void visitNamespace(JetNamespace namespace) {
                    WritableScope namespaceScope = namespaceScopes.get(namespace);
                    processBehaviorDeclarators(namespaceScope, namespace.getDeclarations());
                }

                @Override
                public void visitFunction(JetFunction function) {
                    processFunction(declaringScope, function);
                }

                @Override
                public void visitProperty(JetProperty property) {
                    processProperty(declaringScope, property);
                }

                @Override
                public void visitConstructor(JetConstructor constructor) {
                    DeclarationDescriptor containingDeclaration = declaringScope.getContainingDeclaration();
                    if (containingDeclaration instanceof ClassDescriptor) {
                        processConstructor((MutableClassDescriptor) containingDeclaration, constructor);
                    }
                    else {
                        semanticServices.getErrorHandler().genericError(constructor.getNode(), "Constructors are only allowed inside classes");
                    }
                }

                @Override
                public void visitDeclaration(JetDeclaration dcl) {
                    semanticServices.getErrorHandler().genericError(dcl.getNode(), "Unsupported declaration: " + dcl); // TODO
                }
            });
        }

    }

    private void processPrimaryConstructor(MutableClassDescriptor classDescriptor, JetClass klass) {
        // TODO : not all the parameters are real properties
        WritableScope memberScope = classDescriptor.getUnsubstitutedMemberScope(); // TODO : this is REALLY questionable
        ConstructorDescriptor constructorDescriptor = classDescriptorResolver.resolvePrimaryConstructor(memberScope, classDescriptor, klass);
        for (JetParameter parameter : klass.getPrimaryConstructorParameters()) {
            PropertyDescriptor propertyDescriptor = classDescriptorResolver.resolvePrimaryConstructorParameterToAProperty(
                    classDescriptor,
                    memberScope,
                    parameter
            );
            memberScope.addPropertyDescriptor(
                    propertyDescriptor);
        }
        if (constructorDescriptor != null) {
            classDescriptor.addConstructor(constructorDescriptor);
        }
    }

    private void processConstructor(MutableClassDescriptor classDescriptor, JetConstructor constructor) {
        ConstructorDescriptor constructorDescriptor = classDescriptorResolver.resolveConstructorDescriptor(classDescriptor.getUnsubstitutedMemberScope(), classDescriptor, constructor, false);
        classDescriptor.addConstructor(constructorDescriptor);
        functions.put(constructor, constructorDescriptor);
        declaringScopes.put(constructor, classDescriptor.getUnsubstitutedMemberScope());
    }

    private void processFunction(@NotNull WritableScope declaringScope, JetFunction function) {
        declaringScopes.put(function, declaringScope);
        FunctionDescriptor descriptor = classDescriptorResolver.resolveFunctionDescriptor(declaringScope.getContainingDeclaration(), declaringScope, function);
        declaringScope.addFunctionDescriptor(descriptor);
        functions.put(function, descriptor);
    }

    private void processProperty(WritableScope declaringScope, JetProperty property) {
        declaringScopes.put(property, declaringScope);
        PropertyDescriptor descriptor = classDescriptorResolver.resolvePropertyDescriptor(declaringScope.getContainingDeclaration(), declaringScope, property);
        declaringScope.addPropertyDescriptor(descriptor);
    }

    private void processClassObject(JetClassObject classObject) {
        throw new UnsupportedOperationException(); // TODO
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void resolveBehaviorDeclarationBodies() {
        for (Map.Entry<JetDeclaration, FunctionDescriptor> entry : functions.entrySet()) {
            JetDeclaration declaration = entry.getKey();
            FunctionDescriptor descriptor = entry.getValue();

            WritableScope declaringScope = declaringScopes.get(declaration);
            assert declaringScope != null;


            assert declaration instanceof JetFunction || declaration instanceof JetConstructor;
            JetDeclarationWithBody declarationWithBody = (JetDeclarationWithBody) declaration;

            JetExpression bodyExpression = declarationWithBody.getBodyExpression();

            if (declaration instanceof JetFunction) {
                JetFunction function = (JetFunction) declaration;
                FunctionDescriptorImpl functionDescriptorImpl = (FunctionDescriptorImpl) descriptor;
                if (bodyExpression != null) {
                    JetFlowInformationProvider flowInformationProvider = computeFlowData(declaration, bodyExpression);
                    JetTypeInferrer typeInferrer = semanticServices.getTypeInferrer(trace, flowInformationProvider);

                    assert readyToProcessExpressions : "Must be ready collecting types";

                    if (function.getReturnTypeRef() != null) {
                        typeInferrer.checkFunctionReturnType(declaringScope, function, descriptor);
                    }
                    else {
                        JetType returnType = typeInferrer.getFunctionReturnType(declaringScope, function, descriptor);
                        if (returnType == null) {
                            returnType = ErrorUtils.createErrorType("Unable to infer body type");
                        }
                        functionDescriptorImpl.setUnsubstitutedReturnType(returnType);
                    }
                }
                else {
                    if (function.getReturnTypeRef() == null) {
                        semanticServices.getErrorHandler().genericError(function.getNode(), "This function must either declare a return type or have a body element");
                        ((FunctionDescriptorImpl) descriptor).setUnsubstitutedReturnType(ErrorUtils.createErrorType("No type, no body"));
                    }
                }
            }
            else if (declaration instanceof JetConstructor) {
                if (bodyExpression != null) {
                    computeFlowData(declaration, bodyExpression);
                    JetFlowInformationProvider flowInformationProvider = computeFlowData(declaration, bodyExpression);
                    JetTypeInferrer typeInferrer = semanticServices.getTypeInferrer(trace, flowInformationProvider);
                    typeInferrer.getType(FunctionDescriptorUtil.getFunctionInnerScope(declaringScope, descriptor, semanticServices), bodyExpression, true);
                }

            }
            assert descriptor.getUnsubstitutedReturnType() != null;
        }
    }

    private JetFlowInformationProvider computeFlowData(@NotNull JetDeclaration declaration, @NotNull JetExpression bodyExpression) {
        final JetPseudocodeTrace pseudocodeTrace = flowDataTraceFactory.createTrace(declaration);
        final Map<JetElement, Pseudocode> pseudocodeMap = new HashMap<JetElement, Pseudocode>();
        JetPseudocodeTrace wrappedTrace = new JetPseudocodeTrace() {
            @Override
            public void recordControlFlowData(@NotNull JetElement element, @NotNull Pseudocode pseudocode) {
                pseudocodeTrace.recordControlFlowData(element, pseudocode);
                pseudocodeMap.put(element, pseudocode);
            }

            @Override
            public void close() {
                pseudocodeTrace.close();
                for (Pseudocode pseudocode : pseudocodeMap.values()) {
                    pseudocode.postProcess();
                }
            }
        };
        JetControlFlowInstructionsGenerator instructionsGenerator = new JetControlFlowInstructionsGenerator(wrappedTrace);
        new JetControlFlowProcessor(semanticServices, trace, instructionsGenerator).generate(declaration, bodyExpression);
        wrappedTrace.close();
        return new JetFlowInformationProvider() {
            @Override
            public void collectReturnedInformation(@NotNull JetFunction function, Collection<JetExpression> returnedExpressions, Collection<JetElement> elementsReturningUnit) {
                Pseudocode pseudocode = pseudocodeMap.get(function);
                assert pseudocode != null;

                SubroutineExitInstruction exitInstruction = pseudocode.getExitInstruction();
                processPreviousInstructions(exitInstruction, returnedExpressions, elementsReturningUnit);
            }
        };
    }

    private void processPreviousInstructions(Instruction previousFor, final Collection<JetExpression> returnedExpressions, final Collection<JetElement> elementsReturningUnit) {
        Collection<Instruction> previousInstructions = previousFor.getPreviousInstructions();
        InstructionVisitor visitor = new InstructionVisitor() {
            @Override
            public void visitReadValue(ReadValueInstruction instruction) {
                returnedExpressions.add((JetExpression) instruction.getElement());
            }

            @Override
            public void visitReturnValue(ReturnValueInstruction instruction) {
                processPreviousInstructions(instruction, returnedExpressions, elementsReturningUnit);
            }

            @Override
            public void visitReturnNoValue(ReturnNoValueInstruction instruction) {
                elementsReturningUnit.add(instruction.getElement());
            }

            @Override
            public void visitSubroutineEnter(SubroutineEnterInstruction instruction) {
                elementsReturningUnit.add(instruction.getSubroutine());
            }

            @Override
            public void visitUnsupportedElementInstruction(UnsupportedElementInstruction instruction) {
                semanticServices.getErrorHandler().genericError(instruction.getElement().getNode(), "Unsupported by control-flow builder");
            }

            @Override
            public void visitWriteValue(WriteValueInstruction writeValueInstruction) {
                elementsReturningUnit.add(writeValueInstruction.getElement());
            }

            @Override
            public void visitJump(AbstractJumpInstruction instruction) {
                processPreviousInstructions(instruction, returnedExpressions, elementsReturningUnit);
            }

            @Override
            public void visitReadUnitValue(ReadUnitValueInstruction instruction) {
                returnedExpressions.add((JetExpression) instruction.getElement());
            }

            @Override
            public void visitInstruction(Instruction instruction) {
                if (instruction instanceof JetElementInstruction) {
                    JetElementInstruction elementInstruction = (JetElementInstruction) instruction;
                    semanticServices.getErrorHandler().genericError(elementInstruction.getElement().getNode(), "Unsupported by control-flow builder " + elementInstruction.getElement());
                }
                else {
                    throw new UnsupportedOperationException(instruction.toString());
                }
            }
        };
        for (Instruction previousInstruction : previousInstructions) {
            previousInstruction.accept(visitor);
        }
    }

}
