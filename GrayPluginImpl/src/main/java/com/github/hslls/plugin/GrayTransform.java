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

    private GrayConfig mGrayConfig;

    public GrayTransform(Project project) {
        super(project);
        mGrayConfig = mProject.getExtensions().getByType(GrayConfig.class);
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
        println(mGrayConfig, "jarFilePath " + jarFilePath);
        if (jarFilePath.contains("appcompat")) {
            try {
                ClassPool.getDefault().appendClassPath(jarFilePath);
                println(mGrayConfig, "appendClassPath " + jarFilePath);
            } catch (NotFoundException e) {
                println(mGrayConfig, "appendClassPath failed " + jarFilePath + " " + e.getMessage());
            }
        }
        if (jarFilePath.contains("build/intermediates/runtime_library_classes")) {
            return true;
        } else {
            if (mGrayConfig.modifyJars != null) {
                for (String s : mGrayConfig.modifyJars) {
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
            println(mGrayConfig, "justModifyNotWriteBack 1 " + ctClass.getName());
            return false;
        }

        FindMethodResult findOnCreateResult = findMethod(mGrayConfig, ctClass,
                "onCreate", "(Landroid/os/Bundle;)V");
        if (findOnCreateResult.notFound()) {
            println(mGrayConfig, "justModifyNotWriteBack 2 " + ctClass.getName());
            return false;
        }

        FindMethodResult findGetWindowResult = findMethod(mGrayConfig, ctClass,
                "getWindow", "()Landroid/view/Window;");
        FindMethodResult findFindViewByIdResult = findMethod(mGrayConfig, ctClass,
                "findViewById", "(I)Landroid/view/View;");
        if (findGetWindowResult.notFound() && findFindViewByIdResult.notFound()) {
            println(mGrayConfig, "justModifyNotWriteBack 3 " + ctClass.getName());
            return false;
        }

        if (findOnCreateResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
            if (!addOverrideMethod(mGrayConfig, ctClass, findOnCreateResult.mFindMethod)) {
                println(mGrayConfig, "justModifyNotWriteBack 4 " + ctClass.getName());
                return false;
            }
        }

        boolean hasGetWindowMethod = false;
        if (findGetWindowResult.mStatus == FindMethodStatus.IN_THIS_CLASS) {
            hasGetWindowMethod = true;
        } else if (findGetWindowResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
            if (addOverrideMethod(mGrayConfig, ctClass, findGetWindowResult.mFindMethod)) {
                hasGetWindowMethod = true;
            }
        }
        if (hasGetWindowMethod) {
            if (!addGetDecorViewMethod1(mGrayConfig, ctClass)) {
                println(mGrayConfig, "justModifyNotWriteBack 5 " + ctClass.getName());
                return false;
            }
        } else {
            boolean hasFindViewByIdMethod = false;
            if (findFindViewByIdResult.mStatus == FindMethodStatus.IN_THIS_CLASS) {
                hasFindViewByIdMethod = true;
            } else if (findFindViewByIdResult.mStatus == FindMethodStatus.IN_SUPER_CLASS) {
                if (addOverrideMethod(mGrayConfig, ctClass, findFindViewByIdResult.mFindMethod)) {
                    hasFindViewByIdMethod = true;
                }
            }
            if (hasFindViewByIdMethod) {
                if (!addGetDecorViewMethod2(mGrayConfig, ctClass)) {
                    println(mGrayConfig, "justModifyNotWriteBack 6 " + ctClass.getName());
                    return false;
                }
            } else {
                println(mGrayConfig, "justModifyNotWriteBack 7 " + ctClass.getName());
                return false;
            }
        }

        if (!addGetTurnGrayMethod(mGrayConfig, ctClass)) {
            println(mGrayConfig, "justModifyNotWriteBack 8 " + ctClass.getName());
            return false;
        }

        try {
            findOnCreateResult.mFindMethod.insertAfter("turnGray(getDecorView());");
        } catch (CannotCompileException e) {
            println(mGrayConfig, "justModifyNotWriteBack 9 " + ctClass.getName() + " " + e.getReason());
            return false;
        }

        println(mGrayConfig, "justModifyNotWriteBack success " + ctClass.getName());
        return true;
    }

    private static void println(GrayConfig config, String x) {
        if (!config.printDebugInfo) {
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

    private static FindMethodResult findMethod(GrayConfig config, CtClass ctClass, String methodName, String methodDesc) {
        FindMethodResult result = new FindMethodResult();
        try {
            result.mFindMethod = ctClass.getMethod(methodName, methodDesc);
            CtClass ctDeclaringClass = result.mFindMethod.getDeclaringClass();
            if (ctDeclaringClass != null) {
                result.mStatus = (ctClass.getName().equals(ctDeclaringClass.getName())) ?
                        FindMethodStatus.IN_THIS_CLASS : FindMethodStatus.IN_SUPER_CLASS;
            }
        } catch (NotFoundException e) {
            println(config, "findMethod " + ctClass.getName() + " " + e.getMessage());
        }
        return result;
    }

    private static boolean addOverrideMethod(GrayConfig config, CtClass ctClass, CtMethod method) {
        try {
            CtMethod methodOverride = CtNewMethod.delegator(method, ctClass);
            ctClass.addMethod(methodOverride);
        } catch (CannotCompileException e) {
            println(config, "addOverrideMethod " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetDecorViewMethod1(GrayConfig config, CtClass ctClass) {
        CtMethod method;
        try {
            method = CtNewMethod.make(
                    "private android.view.View getDecorView() {" +
                            "return getWindow().getDecorView();" +
                            "}",
                    ctClass);
            ctClass.addMethod(method);
        } catch (CannotCompileException e) {
            println(config, "addGetDecorViewMethod1 " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetDecorViewMethod2(GrayConfig config, CtClass ctClass) {
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
            println(config, "addGetDecorViewMethod2 " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean addGetTurnGrayMethod(GrayConfig config, CtClass ctClass) {
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
            println(config, "addGetTurnGrayMethod " + ctClass.getName() + " " + e.getMessage());
            return false;
        }
        return true;
    }
}