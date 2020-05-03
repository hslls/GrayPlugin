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

    private GrayConfigs mGrayConfigs;

    public GrayTransform(Project project) {
        super(project);
        mGrayConfigs = mProject.getExtensions().getByType(GrayConfigs.class);
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
        println(mGrayConfigs, "jarFilePath " + jarFilePath);
        boolean appendClassPath = false;
        if (jarFilePath.contains("appcompat")) {
            appendClassPath = true;
        } else {
            if (mGrayConfigs.appendClassPaths != null) {
                for (String s : mGrayConfigs.appendClassPaths) {
                    if (jarFilePath.contains(s)) {
                        appendClassPath = true;
                        break;
                    }
                }
            }
        }
        if (appendClassPath) {
            try {
                ClassPool.getDefault().appendClassPath(jarFilePath);
                println(mGrayConfigs, "appendClassPath " + jarFilePath);
            } catch (NotFoundException e) {
                println(mGrayConfigs, "appendClassPath failed " + jarFilePath + " " + e.getMessage());
            }
        }

        if (jarFilePath.contains("build/intermediates/runtime_library_classes")) {
            return true;
        } else {
            if (mGrayConfigs.modifyJars != null) {
                for (String s : mGrayConfigs.modifyJars) {
                    if (jarFilePath.contains(s)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    protected boolean justModifyNotWriteBack(CtClass ctClass) {
        if (ctClass.isInterface()) {
            println(mGrayConfigs, "justModifyNotWriteBack 1 " + ctClass.getName());
            return false;
        }

        FindMethodResult findOnCreateResult = findMethod(mGrayConfigs, ctClass,
                "onCreate", "(Landroid/os/Bundle;)V");
        if (findOnCreateResult.notFound()) {
            println(mGrayConfigs, "justModifyNotWriteBack 2 " + ctClass.getName());
            return false;
        }

        FindMethodResult findGetWindowResult = findMethod(mGrayConfigs, ctClass,
                "getWindow", "()Landroid/view/Window;");
        FindMethodResult findFindViewByIdResult = findMethod(mGrayConfigs, ctClass,
                "findViewById", "(I)Landroid/view/View;");
        if (findGetWindowResult.notFound() && findFindViewByIdResult.notFound()) {
            println(mGrayConfigs, "justModifyNotWriteBack 3 " + ctClass.getName());
            return false;
        }

        if (findOnCreateResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
            if (!addOverrideMethod(mGrayConfigs, ctClass, findOnCreateResult.mFindMethod)) {
                println(mGrayConfigs, "justModifyNotWriteBack 4 " + ctClass.getName());
                return false;
            }
        }

        boolean hasGetWindowMethod = false;
        if (findGetWindowResult.mStatus == FindMethodStatus.IN_THIS_CLASS) {
            hasGetWindowMethod = true;
        } else if (findGetWindowResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
            if (addOverrideMethod(mGrayConfigs, ctClass, findGetWindowResult.mFindMethod)) {
                hasGetWindowMethod = true;
            }
        }
        if (hasGetWindowMethod) {
            if (!addGetDecorViewMethod1(mGrayConfigs, ctClass)) {
                println(mGrayConfigs, "justModifyNotWriteBack 5 " + ctClass.getName());
                return false;
            }
        } else {
            boolean hasFindViewByIdMethod = false;
            if (findFindViewByIdResult.mStatus == FindMethodStatus.IN_THIS_CLASS) {
                hasFindViewByIdMethod = true;
            } else if (findFindViewByIdResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
                if (addOverrideMethod(mGrayConfigs, ctClass, findFindViewByIdResult.mFindMethod)) {
                    hasFindViewByIdMethod = true;
                }
            }
            if (hasFindViewByIdMethod) {
                if (!addGetDecorViewMethod2(mGrayConfigs, ctClass)) {
                    println(mGrayConfigs, "justModifyNotWriteBack 6 " + ctClass.getName());
                    return false;
                }
            } else {
                println(mGrayConfigs, "justModifyNotWriteBack 7 " + ctClass.getName());
                return false;
            }
        }

        if (!addGetTurnGrayMethod(mGrayConfigs, ctClass)) {
            println(mGrayConfigs, "justModifyNotWriteBack 8 " + ctClass.getName());
            return false;
        }

        try {
            findOnCreateResult.mFindMethod.insertAfter("turnGray(getDecorView());");
        } catch (CannotCompileException e) {
            println(mGrayConfigs, "justModifyNotWriteBack 9 " + ctClass.getName() + " " + e.getReason());
            return false;
        }

        println(mGrayConfigs, "justModifyNotWriteBack success " + ctClass.getName());
        return true;
    }

    private static void println(GrayConfigs configs, String x) {
        if (!configs.printDebugInfo) {
            return;
        }
        System.out.println(x);
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

    private static FindMethodResult findMethod(GrayConfigs configs, CtClass ctClass, String methodName, String methodDesc) {
        FindMethodResult result = new FindMethodResult();
        try {
            result.mFindMethod = ctClass.getMethod(methodName, methodDesc);
            CtClass ctDeclaringClass = result.mFindMethod.getDeclaringClass();
            if (ctDeclaringClass != null) {
                result.mStatus = (ctClass.getName().equals(ctDeclaringClass.getName())) ?
                        FindMethodStatus.IN_THIS_CLASS : FindMethodStatus.IN_SUPER_CLASS;
            }
        } catch (NotFoundException e) {
            println(configs, "findMethod " + ctClass.getName() + " " + e.getMessage());
        }
        return result;
    }

    private static boolean addOverrideMethod(GrayConfigs configs, CtClass ctClass, CtMethod method) {
        try {
            CtMethod methodOverride = CtNewMethod.delegator(method, ctClass);
            ctClass.addMethod(methodOverride);
        } catch (CannotCompileException e) {
            println(configs, "addOverrideMethod " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetDecorViewMethod1(GrayConfigs configs, CtClass ctClass) {
        CtMethod method;
        try {
            method = CtNewMethod.make(
                    "private android.view.View getDecorView() {" +
                            "return getWindow().getDecorView();" +
                            "}",
                    ctClass);
            ctClass.addMethod(method);
        } catch (CannotCompileException e) {
            println(configs, "addGetDecorViewMethod1 " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetDecorViewMethod2(GrayConfigs configs, CtClass ctClass) {
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
            println(configs, "addGetDecorViewMethod2 " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetTurnGrayMethod(GrayConfigs configs, CtClass ctClass) {
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
            println(configs, "addGetTurnGrayMethod " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }
}