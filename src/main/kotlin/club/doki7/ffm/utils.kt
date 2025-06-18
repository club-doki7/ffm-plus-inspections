package club.doki7.ffm

import com.intellij.AbstractBundle
import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.*

class FfmInspectionProvider : InspectionToolProvider {
  override fun getInspectionClasses(): Array<Class<out LocalInspectionTool>> = arrayOf(
  )
}

abstract class FfmInspection : LocalInspectionTool() {
  override fun isEnabledByDefault() = true
  final override fun getGroupDisplayName() = FfmBundle.message("ffm.group.name")
}

object FfmBundle {
  @NonNls private const val BUNDLE = "ffm-plus.ffm+-bundle"
  private val bundle: ResourceBundle by lazy { ResourceBundle.getBundle(BUNDLE) }

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
    AbstractBundle.message(bundle, key, *params)
}

inline fun methodCallVisitor(crossinline f: (PsiMethodCallExpression) -> Unit) =
  object : JavaElementVisitor() {
    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) = f(expression)
  }

inline fun newVisitor(crossinline f: (PsiNewExpression) -> Unit) =
  object : JavaElementVisitor() {
    override fun visitNewExpression(expression: PsiNewExpression) = f(expression)
  }
