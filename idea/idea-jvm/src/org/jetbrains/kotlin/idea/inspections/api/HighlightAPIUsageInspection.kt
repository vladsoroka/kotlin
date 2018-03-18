/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections.api

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*

class HighlightAPIUsageInspection : LocalInspectionTool() {
    var items: MutableList<Api> = arrayListOf(Api("java.lang.String#isEmpty()"))
        set(value) {
            field = value

            forbiddenApiReferences.clear()
            value.mapTo(forbiddenApiReferences, { it.reference })
        }
    var suppressInPackagePattern = "compat"

    private val forbiddenApiReferences: MutableSet<String> = hashSetOf()

    class Api @JvmOverloads constructor(
        var reference: String = "",
        var since: String? = null,
        var reason: String? = null,
        var level: String? = null,
        var replaceReference: String? = null
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return JavaHighlightApiVisitor(holder, this)
    }

    fun isHighlightSignature(signature: String) = signature in forbiddenApiReferences

    companion object {
        fun isInProject(element: PsiElement): Boolean {
            return element.manager.isInProject(element)
        }

        fun registerError(holder: ProblemsHolder, reference: PsiElement?) {
            if (reference != null && isInProject(reference)) {
                holder.registerProblem(reference, "Deprecated API")
            }
        }
    }
}