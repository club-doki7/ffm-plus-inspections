package club.doki7.ffm;

import club.doki7.ffm.annotation.Bitmask;
import club.doki7.ffm.annotation.EnumType;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// Modified from [MagicConstantUtils](https://github.com/JetBrains/intellij-community/blob/master/java/java-impl/src/com/intellij/codeInspection/magicConstant/MagicConstantUtils.java)
/// Removed support for bean info, added support for {@link club.doki7.ffm.annotation.Bitmask} and {@link club.doki7.ffm.annotation.EnumType}
public final class FfmPlusMagicConstantUtils {
  private static AllowedValues getAllowedValuesFromJB(@NotNull PsiType type,
                                                      @NotNull PsiAnnotation magic,
                                                      @NotNull PsiManager manager,
                                                      @Nullable PsiElement context) {
    PsiAnnotationMemberValue[] allowedValues = PsiAnnotationMemberValue.EMPTY_ARRAY;
    boolean values = false;
    boolean flags = false;
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.LONG_RANK) {
      PsiAnnotationMemberValue intValues = magic.findAttributeValue("intValues");
      if (intValues instanceof PsiArrayInitializerMemberValue) {
        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) intValues).getInitializers();
        if (initializers.length != 0) {
          allowedValues = initializers;
          values = true;
        }
      }
      if (!values) {
        PsiAnnotationMemberValue orValue = magic.findAttributeValue("flags");
        if (orValue instanceof PsiArrayInitializerMemberValue) {
          final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) orValue).getInitializers();
          if (initializers.length != 0) {
            allowedValues = initializers;
            flags = true;
          }
        }
      }
    } else if (type.equals(PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(manager.getProject())))) {
      PsiAnnotationMemberValue strValuesAttr = magic.findAttributeValue("stringValues");
      if (strValuesAttr instanceof PsiArrayInitializerMemberValue) {
        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) strValuesAttr).getInitializers();
        if (initializers.length != 0) {
          allowedValues = initializers;
          values = true;
        }
      }
    } else {
      return null; //other types not supported
    }

    PsiAnnotationMemberValue[] valuesFromClass = readFromClass("valuesFromClass", magic, type, manager, context);
    if (valuesFromClass != null) {
      allowedValues = ArrayUtil.mergeArrays(allowedValues, valuesFromClass, PsiAnnotationMemberValue.ARRAY_FACTORY);
      values = true;
    }
    PsiAnnotationMemberValue[] flagsFromClass = readFromClass("flagsFromClass", magic, type, manager, context);
    if (flagsFromClass != null) {
      allowedValues = ArrayUtil.mergeArrays(allowedValues, flagsFromClass, PsiAnnotationMemberValue.ARRAY_FACTORY);
      flags = true;
    }
    if (allowedValues.length == 0) {
      return null;
    }
    if (values && flags) {
      throw new IncorrectOperationException(
          "Misconfiguration of @MagicConstant annotation: 'flags' and 'values' shouldn't be used at the same time");
    }
    return new AllowedValues(allowedValues, flags);
  }

  private static AllowedValues getAllowedValuesFromChuigda(@NotNull PsiType type,
                                                           @NotNull PsiAnnotation ffmPlus,
                                                           @NotNull PsiManager manager,
                                                           @Nullable PsiElement context) {
    var allowedValues = PsiAnnotationMemberValue.EMPTY_ARRAY;
    var flags = false;
    String qualifiedName = ffmPlus.getQualifiedName();
    if (Bitmask.class.getName().equals(qualifiedName)) {
      var values = readFromClass(null, ffmPlus, type, manager, context);
      if (values != null) {
        allowedValues = values;
        flags = true;
      }
    } else /*if (EnumType.class.getName().equals(qualifiedName))*/ {
      var values = readFromClass(null, ffmPlus, type, manager, context);
      if (values != null) allowedValues = values;
    }

    return new AllowedValues(allowedValues, flags);
  }

  /// @param attributeName null if it's just `values`
  private static PsiAnnotationMemberValue[] readFromClass(@NonNls @Nullable String attributeName,
                                                          @NotNull PsiAnnotation magic,
                                                          @NotNull PsiType type,
                                                          @NotNull PsiManager manager,
                                                          @Nullable PsiElement context) {
    // ice1000: modified to "Declared" since there are no default values for these attributes
    PsiAnnotationMemberValue fromClassAttr = magic.findDeclaredAttributeValue(attributeName);
    PsiType fromClassType = fromClassAttr instanceof PsiClassObjectAccessExpression
        ? ((PsiClassObjectAccessExpression) fromClassAttr).getOperand().getType()
        : null;
    PsiClass fromClass = fromClassType instanceof PsiClassType ? ((PsiClassType) fromClassType).resolve() : null;
    if (fromClass == null) return null;
    String fqn = fromClass.getQualifiedName();
    if (fqn == null) return null;
    List<PsiAnnotationMemberValue> constants = new ArrayList<>();
    for (PsiField field : fromClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) continue;
      if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
        if (context == null ||
            !JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper().isAccessible(field, context, null)) {
          continue;
        }
      }
      PsiType fieldType = field.getType();
      if (!Comparing.equal(fieldType, type)) continue;
      PsiAssignmentExpression e = (PsiAssignmentExpression) JavaPsiFacade.getElementFactory(manager.getProject())
          .createExpressionFromText("x=" + fqn + "." + field.getName(), field);
      PsiReferenceExpression refToField = (PsiReferenceExpression) e.getRExpression();
      constants.add(refToField);
    }
    if (constants.isEmpty()) return null;

    return constants.toArray(PsiAnnotationMemberValue.EMPTY_ARRAY);
  }

  /**
   * Generates a user-friendly textual representation of a value based on magic constant annotations, if possible.
   * Must be run inside the read action
   *
   * @param val   value (number or string) which may have a magic constant representation
   * @param owner an owner that produced this value (either the variable which stores it, or a method which returns it)
   * @return a textual representation of the magic constant; null if non-applicable
   */
  @RequiresReadLock
  public static @Nullable String getPresentableText(Object val, @NotNull PsiModifierListOwner owner) {
    if (!(val instanceof String) &&
        !(val instanceof Integer) &&
        !(val instanceof Long) &&
        !(val instanceof Short) &&
        !(val instanceof Byte)) {
      return null;
    }
    PsiType type = PsiUtil.getTypeByPsiElement(owner);
    if (type == null) return null;
    AllowedValues allowedValues = getAllowedValues(owner, type, owner);
    if (allowedValues == null) return null;

    if (!allowedValues.isFlagSet()) {
      for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
        if (value instanceof PsiExpression expression) {
          Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, null, false);
          if (val.equals(constantValue)) {
            return expression instanceof PsiReferenceExpression ref ? ref.getReferenceName() : expression.getText();
          }
        }
      }
    } else {
      if (!(val instanceof Number number)) return null;

      // try to find or-ed flags
      long remainingFlags = number.longValue();
      List<PsiAnnotationMemberValue> flags = new ArrayList<>();
      for (PsiAnnotationMemberValue value : allowedValues.getValues()) {
        if (value instanceof PsiExpression expression) {
          Long constantValue = evaluateLongConstant(expression);
          if (constantValue == null) {
            continue;
          }
          if ((remainingFlags & constantValue) == constantValue) {
            flags.add(value);
            remainingFlags &= ~constantValue;
          }
        }
      }
      if (remainingFlags == 0) {
        // found flags to combine with OR, suggest the fix
        if (flags.size() > 1) {
          for (int i = flags.size() - 1; i >= 0; i--) {
            PsiAnnotationMemberValue flag = flags.get(i);
            Long flagValue = evaluateLongConstant((PsiExpression) flag);
            if (flagValue != null && flagValue == 0) {
              // no sense in ORing with '0'
              flags.remove(i);
            }
          }
        }
        if (!flags.isEmpty()) {
          return StreamEx.of(flags)
              .map(flag -> flag instanceof PsiReferenceExpression ref ? ref.getReferenceName() : flag.getText())
              .joining(" | ");
        }
      }
    }
    return null;
  }

  /**
   * @param element element with possible MagicConstant annotation
   * @param type    element type
   * @param context context where annotation is applied (to check the accessibility of magic constant)
   * @return possible allowed values to be used instead of constant literal; null if no MagicConstant annotation found
   */
  public static @Nullable AllowedValues getAllowedValues(@NotNull PsiModifierListOwner element,
                                                         @Nullable PsiType type,
                                                         @Nullable PsiElement context) {
    return getAllowedValues(element, type, context, null);
  }

  static @Nullable AllowedValues getAllowedValues(@NotNull PsiModifierListOwner element,
                                                  @Nullable PsiType type,
                                                  @Nullable PsiElement context,
                                                  @Nullable Set<? super PsiModifierListOwner> visited) {
    if (visited != null && visited.size() > 5) return null; // Avoid too deep traversal
    PsiManager manager = element.getManager();
    for (PsiAnnotation annotation : getAllAnnotations(element)) {
      if (type != null) {
        if (MagicConstant.class.getName().equals(annotation.getQualifiedName())) {
          AllowedValues values = getAllowedValuesFromJB(type, annotation, manager, context);
          if (values != null) return values;
        }
        if (Bitmask.class.getName().equals(annotation.getQualifiedName()) ||
            EnumType.class.getName().equals(annotation.getQualifiedName())) {
          return getAllowedValuesFromChuigda(type, annotation, manager, context);
        }
      }

      PsiClass aClass = annotation.resolveAnnotationType();
      if (aClass == null) continue;

      if (visited == null) visited = new HashSet<>();
      if (!visited.add(aClass)) continue;
      AllowedValues values = getAllowedValues(aClass, type, context, visited);
      if (values != null) {
        return values;
      }
    }

    if (element instanceof PsiLocalVariable localVariable) {
      PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(localVariable.getInitializer());
      if (initializer != null) {
        PsiModifierListOwner target = null;
        if (initializer instanceof PsiMethodCallExpression call) {
          target = call.resolveMethod();
        } else if (initializer instanceof PsiReferenceExpression ref) {
          target = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
        }
        if (target != null) {
          PsiElement block = PsiUtil.getVariableCodeBlock(localVariable, null);
          if (block != null /*&& ControlFlowUtil.isEffectivelyFinal(localVariable, block)*/) {
            if (visited == null) visited = new HashSet<>();
            if (visited.add(target)) {
              return getAllowedValues(target, type, context, visited);
            }
          }
        }
      }
    }

    return null;
  }

  private static PsiAnnotation @NotNull [] getAllAnnotations(@NotNull PsiModifierListOwner element) {
    PsiModifierListOwner realElement = getSourceElement(element);
    return CachedValuesManager.getCachedValue(realElement, () ->
        CachedValueProvider.Result.create(AnnotationUtil.getAllAnnotations(realElement, true, null, false),
            PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static PsiModifierListOwner getSourceElement(@NotNull PsiModifierListOwner element) {
    if (element instanceof PsiCompiledElement) {
      PsiElement navigationElement = element.getNavigationElement();
      if (navigationElement instanceof PsiModifierListOwner) {
        return (PsiModifierListOwner) navigationElement;
      }
    }
    return element;
  }

  static boolean same(@NotNull PsiElement e1, @NotNull PsiElement e2, @NotNull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression e1Lit && e2 instanceof PsiLiteralExpression e2Lit) {
      return Comparing.equal(e1Lit.getValue(), e2Lit.getValue());
    }
    if (e1 instanceof PsiPrefixExpression e1Pre && e2 instanceof PsiPrefixExpression e2Pre && e1Pre.getOperationTokenType() == e2Pre.getOperationTokenType()) {
      PsiExpression lOperand = e1Pre.getOperand();
      PsiExpression rOperand = e2Pre.getOperand();
      return lOperand != null && rOperand != null && same(lOperand, rOperand, manager);
    }
    if (e1 instanceof PsiReference e1Ref && e2 instanceof PsiReference e2Ref) {
      e1 = e1Ref.resolve();
      e2 = e2Ref.resolve();
    }
    return manager.areElementsEquivalent(e2, e1);
  }

  static Long evaluateLongConstant(@NotNull PsiExpression expression) {
    Object constantValue = JavaConstantExpressionEvaluator.computeConstantExpression(expression, null, false);
    if (constantValue instanceof Long ||
        constantValue instanceof Integer ||
        constantValue instanceof Short ||
        constantValue instanceof Byte) {
      return ((Number) constantValue).longValue();
    }
    return null;
  }

  public static class AllowedValues {
    private final PsiAnnotationMemberValue @NotNull [] values;
    private final boolean canBeOred;
    private final boolean resolvesToZero; //true if one if the values resolves to literal 0, e.g. "int PLAIN = 0"

    AllowedValues(PsiAnnotationMemberValue @NotNull [] values, boolean canBeOred) {
      this.values = values;
      this.canBeOred = canBeOred;
      resolvesToZero = resolvesToZero();
    }

    private boolean resolvesToZero() {
      for (PsiAnnotationMemberValue value : values) {
        if (value instanceof PsiExpression) {
          Object evaluated = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression) value, null, false);
          if (evaluated instanceof Integer && (Integer) evaluated == 0) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AllowedValues a2 = (AllowedValues) o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = ContainerUtil.newHashSet(values);
      Set<PsiAnnotationMemberValue> v2 = ContainerUtil.newHashSet(a2.values);
      if (v1.size() != v2.size()) {
        return false;
      }
      for (PsiAnnotationMemberValue value : v1) {
        for (PsiAnnotationMemberValue value2 : v2) {
          if (same(value, value2, value.getManager())) {
            v2.remove(value2);
            break;
          }
        }
      }
      return v2.isEmpty();
    }

    @Override public int hashCode() {
      int result = Arrays.hashCode(values);
      result = 31 * result + (canBeOred ? 1 : 0);
      return result;
    }

    boolean isSubsetOf(@NotNull AllowedValues other, @NotNull PsiManager manager) {
      return Arrays.stream(values).allMatch(
          value -> Arrays.stream(other.values).anyMatch(otherValue -> same(value, otherValue, manager)));
    }

    public PsiAnnotationMemberValue @NotNull [] getValues() {
      return values;
    }

    /**
     * @return true if values represent a flag set, so can be combined via bitwise or
     */
    public boolean isFlagSet() {
      return canBeOred;
    }

    /**
     * @return true if at least one of values equals to integer 0
     */
    public boolean hasZeroValue() {
      return resolvesToZero;
    }
  }
}