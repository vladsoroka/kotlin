/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections.api

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.ObjectUtils
import org.jetbrains.kotlin.idea.inspections.api.HighlightAPIUsageInspection.Companion.registerError

class JavaHighlightApiVisitor internal constructor(
        private val myHolder: ProblemsHolder,
        private val highlightAPIUsageInspection: HighlightAPIUsageInspection
) : JavaElementVisitor() {
    private val isIgnored: Boolean
        get() = false

    override fun visitDocComment(comment: PsiDocComment) {
        // No references inside doc comment are of interest.
    }

    override fun visitClass(aClass: PsiClass) {}

    override fun visitReferenceExpression(expression: PsiReferenceExpression?) {
        visitReferenceElement(expression)
    }

    override fun visitNameValuePair(pair: PsiNameValuePair) {
        super.visitNameValuePair(pair)
        val reference = pair.reference ?: return

        val resolve = reference.resolve()
        if (resolve !is PsiCompiledElement || resolve !is PsiAnnotationMethod) return

        ModuleUtilCore.findModuleForPsiElement(pair) ?: return

        if (isForbidden((resolve as PsiMember?)!!)) {
            registerError(myHolder, ObjectUtils.notNull(pair.nameIdentifier, pair))
        }
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement?) {
        super.visitReferenceElement(reference)
        val resolved = reference!!.resolve()

        if (resolved is PsiCompiledElement && resolved is PsiMember) {
            val module = ModuleUtilCore.findModuleForPsiElement(reference.element)
            if (module != null) {
                if (isForbidden((resolved as PsiMember?)!!)) {
                    var psiClass: PsiClass? = null
                    val qualifier = reference.qualifier
                    if (qualifier != null) {
                        if (qualifier is PsiExpression) {
                            psiClass = PsiUtil.resolveClassInType(qualifier.type)
                        }
                    } else {
                        psiClass = PsiTreeUtil.getParentOfType(reference, PsiClass::class.java)
                    }
                    if (psiClass != null) {
                        if (isIgnored) return
                    }
                    registerError(myHolder, reference)
                }
            }
        }
    }

    override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        val constructor = expression.resolveConstructor()
        ModuleUtilCore.findModuleForPsiElement(expression) ?: return

        if (constructor is PsiCompiledElement) {
            if (isForbidden(constructor)) {
                registerError(myHolder, expression.classReference)
            }
        }
    }

    override fun visitMethod(method: PsiMethod) {
        super.visitMethod(method)
        val annotation = if (!method.isConstructor) AnnotationUtil.findAnnotation(method, CommonClassNames.JAVA_LANG_OVERRIDE) else null
        if (annotation != null) {
            val module = ModuleUtilCore.findModuleForPsiElement(annotation)
            if (module != null) {
                val methods = method.findSuperMethods()
                for (superMethod in methods) {
                    if (superMethod is PsiCompiledElement) {
                        if (!isForbidden(superMethod)) {
                            return
                        }
                    } else {
                        return
                    }
                }
                if (methods.size > 0) {
                    registerError(myHolder, annotation.nameReferenceElement)
                }
            }
        }
    }

    fun isForbidden(member: PsiMember): Boolean {
        if (member is PsiAnonymousClass) return false
        val containingClass = member.containingClass
        if (containingClass is PsiAnonymousClass) return false
        if (member is PsiClass && !(member.getParent() is PsiClass || member.getParent() is PsiFile)) return false

        val signature = getSignature(member) ?: return false

        return highlightAPIUsageInspection.isHighlightSignature(signature)
    }

    companion object {
        private fun getSignature(member: PsiMember?): String? {
            if (member is PsiClass) {
                return member.qualifiedName
            }
            if (member is PsiField) {
                val containingClass = getSignature(member.containingClass)
                return if (containingClass == null) null else containingClass + "#" + member.name
            }
            if (member is PsiMethod) {
                val method = member as PsiMethod?
                val containingClass = getSignature(member.containingClass) ?: return null

                val buf = StringBuilder()
                buf.append(containingClass)
                buf.append('#')
                buf.append(method!!.name)
                buf.append('(')
                for (type in method.getSignature(PsiSubstitutor.EMPTY).parameterTypes) {
                    buf.append(type.canonicalText)
                    buf.append(";")
                }
                buf.append(')')
                return buf.toString()
            }
            return null
        }
    }
}