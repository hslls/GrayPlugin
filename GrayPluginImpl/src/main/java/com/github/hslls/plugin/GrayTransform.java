package com.github.hslls.plugin;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.github.hlls.transform.EasyTransform;
import com.google.common.collect.ImmutableSet;

import org.gradle.api.Project;

import java.io.File;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

/**
 * @author <a href="mailto:249418416@qq.com">mapleleaf</a>
 */
public class GrayTransform extends EasyTransform {

    public GrayTransform(Project project) {
        super(project);
    }

    @Override
    public String getName() {
        return "GrayTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
//        return TransformManager.SCOPE_FULL_PROJECT;
        return ImmutableSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                QualifiedContent.Scope.PROJECT, QualifiedContent.Scope.SUB_PROJECTS);
    }

    @Override
    protected boolean isJarFileNeedModify(File jarFile) {
        String jarFilePath = jarFile.getAbsolutePath();
        System.out.println("jarFilePath " + jarFilePath);
        if (jarFilePath.contains("appcompat")) {
            try {
                ClassPool.getDefault().appendClassPath(jarFilePath);
            } catch (NotFoundException e) {

            }
        }
        return jarFilePath.contains("build/intermediates/runtime_library_classes");
    }

    @Override
    protected boolean justModifyNotWriteBack(CtClass ctClass) {
        if (ctClass.isInterface()) {
            System.out.println("justModifyNotWriteBack 1 " + ctClass.getName());
            return false;
        }

        FindMethodResult findOnCreateResult = findMethod(ctClass, "onCreate",
                "(Landroid/os/Bundle;)V");
        if (findOnCreateResult.notFound()) {
            System.out.println("justModifyNotWriteBack 2 " + ctClass.getName());
            return false;
        }

        FindMethodResult findGetWindowResult = findMethod(ctClass, "getWindow",
                "()Landroid/view/Window;");
        FindMethodResult findFindViewByIdResult = findMethod(ctClass, "findViewById",
                "(I)Landroid/view/View;");
        if (findGetWindowResult.notFound() && findFindViewByIdResult.notFound()) {
            System.out.println("justModifyNotWriteBack 3 " + ctClass.getName());
            return false;
        }

        if (findOnCreateResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
            if (!addOverrideMethod(ctClass, findOnCreateResult.mFindMethod)) {
                System.out.println("justModifyNotWriteBack 4 " + ctClass.getName());
                return false;
            }
        }

        boolean hasGetWindowMethod = false;
        if (findGetWindowResult.mStatus == FindMethodStatus.IN_THIS_CLASS) {
            hasGetWindowMethod = true;
        } else if (findGetWindowResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
            if (addOverrideMethod(ctClass, findGetWindowResult.mFindMethod)) {
                hasGetWindowMethod = true;
            }
        }
        if (hasGetWindowMethod) {
            if (!addGetDecorViewMethod1(ctClass)) {
                System.out.println("justModifyNotWriteBack 5 " + ctClass.getName());
                return false;
            }
        } else {
            boolean hasFindViewByIdMethod = false;
            if (findFindViewByIdResult.mStatus == FindMethodStatus.IN_THIS_CLASS) {
                hasFindViewByIdMethod = true;
            } else if (findFindViewByIdResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
                if (addOverrideMethod(ctClass, findFindViewByIdResult.mFindMethod)) {
                    hasFindViewByIdMethod = true;
                }
            }
            if (hasFindViewByIdMethod) {
                if (!addGetDecorViewMethod2(ctClass)) {
                    System.out.println("justModifyNotWriteBack 6 " + ctClass.getName());
                    return false;
                }
            } else {
                System.out.println("justModifyNotWriteBack 7 " + ctClass.getName());
                return false;
            }
        }

        if (!addGetTurnGrayMethod(ctClass)) {
            System.out.println("justModifyNotWriteBack 8 " + ctClass.getName());
            return false;
        }

        try {
            findOnCreateResult.mFindMethod.insertAfter("turnGray(getDecorView());");
        } catch (CannotCompileException e) {
            System.out.println("justModifyNotWriteBack 9 " + ctClass.getName() + " " + e.getReason());
            return false;
        }

        System.out.println("justModifyNotWriteBack success " + ctClass.getName());
        return true;
    }

    private enum FindMethodStatus {

        NOT_FOUND,
        IN_THIS_CLASS,
        IN_SUPER_CLASS

    }

    private static class FindMethodResult {

        private FindMethodStatus mStatus = FindMethodStatus.NOT_FOUND;
        private CtMethod mFindMethod;

        private boolean notFound() {
            return ((mStatus == FindMethodStatus.NOT_FOUND) || (mFindMethod == null));
        }
    }

    private static FindMethodResult findMethod(CtClass ctClass, String methodName, String methodDesc) {
        FindMethodResult result = new FindMethodResult();
        try {
            result.mFindMethod = ctClass.getMethod(methodName, methodDesc);
            CtClass ctDeclaringClass = result.mFindMethod.getDeclaringClass();
            if (ctDeclaringClass != null) {
                result.mStatus = (ctClass.getName().equals(ctDeclaringClass.getName())) ?
                        FindMethodStatus.IN_THIS_CLASS : FindMethodStatus.IN_SUPER_CLASS;
            }
        } catch (NotFoundException e) {
            System.out.println("findMethod " + ctClass.getName() + " " + e.getMessage());
        }
        return result;
    }

    private static boolean addOverrideMethod(CtClass ctClass, CtMethod method) {
        try {
            CtMethod methodOverride = CtNewMethod.delegator(method, ctClass);
            ctClass.addMethod(methodOverride);
        } catch (CannotCompileException e) {
            System.out.println("addOverrideMethod " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetDecorViewMethod1(CtClass ctClass) {
        CtMethod method;
        try {
            method = CtNewMethod.make(
                    "private android.view.View getDecorView() {" +
                            "return getWindow().getDecorView();" +
                            "}",
                    ctClass);
            ctClass.addMethod(method);
        } catch (CannotCompileException e) {
            System.out.println("addGetDecorViewMethod1 " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetDecorViewMethod2(CtClass ctClass) {
        CtMethod method;
        try {
            method = CtNewMethod.make(
                    "private android.view.View getDecorView() {" +
                            "android.view.View cv = findViewById(android.R.id.content);" +
                            "android.view.View dv = cv;" +
                            "if (cv != null) {" +
                            "android.view.ViewParent vp = cv.getParent();" +
                            "while (vp instanceof android.view.View) {" +
                            "dv = (android.view.View) vp;" +
                            "vp = vp.getParent();" +
                            "}" +
                            "}" +
                            "return dv;" +
                            "}",
                    ctClass);
            ctClass.addMethod(method);
        } catch (CannotCompileException e) {
            System.out.println("addGetDecorViewMethod2 " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetTurnGrayMethod(CtClass ctClass) {
        CtMethod method;
        try {
            method = CtNewMethod.make(
                    "private void turnGray(android.view.View decorView) {" +
                            "if (decorView == null) {" +
                            "return;" +
                            "}" +
                            "android.graphics.Paint paint = new android.graphics.Paint();" +
                            "android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();" +
                            "cm.setSaturation(0f);" +
                            "paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));" +
                            "decorView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, paint);" +
                            "}",
                    ctClass);
            ctClass.addMethod(method);
        } catch (CannotCompileException e) {
            System.out.println("addGetTurnGrayMethod " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }
}