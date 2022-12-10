package com.linuxgods.kreiger.refactoring;

import com.intellij.codeInsight.intention.impl.InvertIfConditionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.siyeh.ig.psiutils.ControlFlowUtils.getLastStatementInBlock;

public class SaneIfElseInspection extends LocalInspectionTool {

    @Override
    public @Nullable @Nls String getStaticDescription() {
        return "Checks for sane if-else";
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        
        return new JavaElementVisitor() {
            @Override
            public void visitIfStatement(PsiIfStatement ifStatement) {
                PsiElement parent = ifStatement.getParent();
                if (!(parent instanceof PsiCodeBlock parentBlock)) return;
                PsiStatement thenBranch = ifStatement.getThenBranch();
                PsiStatement elseBranch = ifStatement.getElseBranch();
                if (!(thenBranch instanceof PsiBlockStatement thenBlockStatement)) return;
                if (!(elseBranch instanceof PsiBlockStatement elseBlockStatement)) return;
                PsiCodeBlock thenBlock = thenBlockStatement.getCodeBlock();
                PsiCodeBlock elseBlock = elseBlockStatement.getCodeBlock();
                if (!(parentBlock.getParent() instanceof PsiMethod method)) {
                    return;
                }
                if (thenBlock.getText().lines().count() <= elseBlock.getText().lines().count()) return;

                if (getNextStatement(ifStatement) == null) {
                    holder.registerProblem(ifStatement, "Then branch should be shorter then else branch",
                            new SaneIfElseQuickFix(ifStatement, method));
                }
            }
        };
    }

    private @Nullable PsiStatement getNextStatement(PsiIfStatement statement) {
        while (true) {
            PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                PsiIfStatement parentIfStatement = (PsiIfStatement) parent;
                PsiStatement elseBranch = parentIfStatement.getElseBranch();
                boolean statementIsElseBranch = elseBranch == statement;
                if (statementIsElseBranch) {
                    statement = parentIfStatement;
                    continue;
                }
            }

            return (PsiStatement) PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
        }
    }

    private static class SaneIfElseQuickFix implements LocalQuickFix {
        private final PsiIfStatement ifStatement;
        private final PsiMethod method;

        public SaneIfElseQuickFix(PsiIfStatement ifStatement, PsiMethod method) {
            this.ifStatement = ifStatement;
            this.method = method;
        }

        @Override
        public @IntentionFamilyName @NotNull String getFamilyName() {
            return "Invert if";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            new InvertIfConditionAction().invoke(project, null, ifStatement.getFirstChild());
            PsiStatement thenBranch = ifStatement.getThenBranch();
            if (!(thenBranch instanceof PsiBlockStatement thenBlockStatement)) return;
            PsiCodeBlock thenBlock = thenBlockStatement.getCodeBlock();
            PsiStatement lastStatementInThenBlock = getLastStatementInBlock(thenBlock);
            if (!(lastStatementInThenBlock instanceof PsiThrowStatement) && !(lastStatementInThenBlock instanceof PsiReturnStatement)) {
                if (!PsiType.VOID.equals(method.getReturnType())) {
                    return;
                }
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                thenBlock.addBefore(factory.createStatementFromText("return;", thenBlock),
                        thenBlock.getLastChild());
            }
            unwrapRedundantElse();
        }

        private void unwrapRedundantElse() {
            PsiStatement elseBranch = ifStatement.getElseBranch();
            if (elseBranch == null) return;
            PsiElement anchor = ifStatement;

            PsiElement parent;
            for (parent = ifStatement.getParent(); parent instanceof PsiIfStatement; parent = parent.getParent()) {
                anchor = parent;
            }

            if (!(elseBranch instanceof PsiBlockStatement)) {
                parent.addAfter(elseBranch, anchor);
            } else {
                PsiBlockStatement elseBlockStatement = (PsiBlockStatement) elseBranch;
                PsiCodeBlock elseBlock = elseBlockStatement.getCodeBlock();
                PsiElement[] children = elseBlock.getChildren();
                if (children.length > 2) {
                    parent.addRangeAfter(children[1], children[children.length - 2], anchor);
                }
            }

            elseBranch.delete();
        }
    }
}
